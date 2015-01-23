#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=6) uniform sampler2D shadowMap;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;
uniform vec3 materialDiffuseColor = vec3(0,0,0);

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightAmbient;

uniform bool hasDiffuseMap;
uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;
uniform bool hasNormalMap;
uniform float normalMapWidth = 1;
uniform float normalMapHeight = 1;

uniform float metallic = 0;
uniform float roughness = 1;

const int pointLightMaxCount = 20;
uniform int activePointLightCount = 0;
uniform vec3[pointLightMaxCount] pointLightPositions;
uniform vec3[pointLightMaxCount] pointLightColors;
uniform float[pointLightMaxCount] pointLightRadiuses;

const int areaLightMaxCount = 5;
uniform int activeAreaLightCount = 0;
uniform vec3[areaLightMaxCount] areaLightPositions;
uniform vec3[areaLightMaxCount] areaLightColors;
uniform vec3[areaLightMaxCount] areaLightWidthHeightRanges;
uniform vec3[areaLightMaxCount] areaLightViewDirections;
uniform vec3[areaLightMaxCount] areaLightUpDirections;
uniform vec3[areaLightMaxCount] areaLightRightDirections;

uniform int probeIndex = 0;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec4 position_clip;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;

layout(location=0)out vec4 out_color;

mat3 cotangent_frame( vec3 N, vec3 p, vec2 uv )
{
	vec3 dp1 = dFdx( p );
	vec3 dp2 = dFdy( p );
	vec2 duv1 = dFdx( uv );
	vec2 duv2 = dFdy( uv );
	
	vec3 dp2perp = cross( dp2, N );
	vec3 dp1perp = cross( N, dp1 );
	vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
	
	float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
	return mat3( T * invmax, B * invmax, N );
}
vec3 perturb_normal( vec3 N, vec3 V, vec2 texcoord )
{
	vec3 map = (textureLod(normalMap, texcoord, 0)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}

float linearizeDepth(float z)
{
  float n = 0.1; // camera z near TODO: MAKE THIS UNIFORRRRRRRMMMM
  float f = 500; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}
#define kPI 3.1415926536f
vec3 decode(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}
vec2 encode(vec3 n) {
	//n = vec3(n*0.5+0.5);
    return (vec2((atan(n.x, n.y)/kPI), n.z)+vec2(1,1))*0.5;
}

vec3 projectOnPlane(in vec3 p, in vec3 pc, in vec3 pn)
{
    float distance = dot(pn, p-pc);
    return p - distance*pn;
}
int sideOfPlane(in vec3 p, in vec3 pc, in vec3 pn){
   if (dot(p-pc,pn)>=0.0) return 1; else return 0;
}
vec3 linePlaneIntersect(in vec3 lp, in vec3 lv, in vec3 pc, in vec3 pn){
   return lp+lv*(dot(pn,pc-lp)/dot(pn,lv));
}
vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return vec3(0,0,0);
	}
	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = texture2D(shadowMap,ShadowCoordPostW.xy);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;
	//moments = blur(shadowMap, ShadowCoordPostW.xy, moments.y).rg;
	//moments = textureLod(shadowMap, ShadowCoordPostW.xy, 0).rg;
	//moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y * 0.002).rg;
	//moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y * 0.005).rg;
	//moments /= 3;
	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist < moments.x) {
		return vec3(1.0,1.0,1.0);
	}

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.00012);

	float d = dist - moments.x;
	float p_max = (variance / (variance + d*d));

	return vec3(p_max,p_max,p_max);
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-position);
	V = ViewVector;
 	vec3 L = -normalize((vec4(lightDirection, 0)).xyz);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);
	
	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	float alpha = roughness*roughness;
	float alphaSquare = alpha*alpha;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float denom = (NdotH*NdotH*(alphaSquare-1))+1;
	float D = alphaSquare/(3.1416*denom*denom);
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
    
    // Schlick
    float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2, electrics up to 1 and mostly above 0.5
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(L, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	diff = (diff.rgb) * (1-F0);
	
	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	return diff + lightDiffuse.rgb * specularColor * cookTorrance;
}

float calculateAttenuation(float dist, float lightRadius) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}

vec3 cookTorranceAreaLight(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDiffuse,
							   vec3 lightPosition, int index, vec3 diffuseColor, vec3 specularColor) {

//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = -ViewVector;
	
	vec3 lightViewDirection = areaLightViewDirections[index];
	vec3 lightUpDirection = areaLightUpDirections[index];
	vec3 lightRightDirection = areaLightRightDirections[index];
	vec3 lightWidhtHeightRange = areaLightWidthHeightRanges[index];
	float lightWidth = lightWidhtHeightRange.x;
	float lightHeight = lightWidhtHeightRange.y;
	float lightRange = lightWidhtHeightRange.z;
	
	vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
	vec3 light_view_direction_eye = (viewMatrix * vec4(lightViewDirection, 0)).xyz;
	vec3 light_up_direction_eye = (viewMatrix * vec4(lightUpDirection, 0)).xyz;
	vec3 light_right_direction_eye = (viewMatrix * vec4(lightRightDirection, 0)).xyz;
	
	vec3 lVector[ 4 ];
	vec3 leftUpper = light_position_eye + (lightWidth/2)*(-light_right_direction_eye) + (lightHeight/2)*(light_up_direction_eye);
	vec3 rightUpper = light_position_eye + (lightWidth/2)*(light_right_direction_eye) + (lightHeight/2)*(light_up_direction_eye);
	vec3 leftBottom = light_position_eye + (lightWidth/2)*(-light_right_direction_eye) + (lightHeight/2)*(-light_up_direction_eye);
	vec3 rightBottom = light_position_eye + (lightWidth/2)*(light_right_direction_eye) + (lightHeight/2)*(-light_up_direction_eye);
	
	lVector[0] = normalize(leftUpper - position);
	lVector[1] = normalize(rightUpper - position);
	lVector[3] = normalize(leftBottom - position);
	lVector[2] = normalize(rightBottom - position); // clockwise oriented all the lights rectangle points
	float tmp = dot( lVector[ 0 ], cross( ( leftBottom - leftUpper ).xyz, ( rightUpper - leftUpper ).xyz ) );
	if ( tmp > 0.0 ) {
		return vec3(0,0,0);
	} else {
		vec3 lightVec = vec3( 0.0 );
		for( int i = 0; i < 4; i ++ ) {
	
			vec3 v0 = lVector[ i ];
			vec3 v1 = lVector[ int( mod( float( i + 1 ), float( 4 ) ) ) ]; // ugh...
			lightVec += acos( dot( v0, v1 ) ) * normalize( cross( v0, v1 ) );
	
		}
		
	 	vec3 L = lightVec;
	    vec3 H = normalize(L + V);
    	vec3 N = normal;
	    vec3 P = position;
        vec3 R = reflect(V, N);
        vec3 E = linePlaneIntersect(position, R, light_position_eye, light_view_direction_eye);

		float width = lightWidth;
	    float height = lightHeight;
	    vec3 projection = projectOnPlane(position, light_position_eye, light_view_direction_eye);
	    vec3 dir = projection-light_position_eye;
	    vec2 diagonal = vec2(dot(dir,light_right_direction_eye),dot(dir,light_up_direction_eye));
	    vec2 nearest2D = vec2(clamp(diagonal.x, -width, width),clamp(diagonal.y, -height, height));
	    
	    // this is the amount of space the projected point is away from the border of the area light.
	    // if the term is positive, the trace is besides the broder. Up to 5 units linear fading the specular, so that
	    // we have glossy specular on rough surfaces
	    vec2 overheadVec2 = (vec2(abs(diagonal.x)-width, abs(diagonal.y)-height) / 5);
	    float overhead = clamp(max(overheadVec2.x, overheadVec2.y), 0.0, 1.0);
	    
	    vec3 nearestPointInside = light_position_eye + (light_right_direction_eye * nearest2D.x + light_up_direction_eye * nearest2D.y);
	    //if(distance(P, nearestPointInside) > lightRange) { discard; }
	    vec2 texCoords = nearest2D / vec2(width, height);
        texCoords += 1;
	    texCoords /=2;
        float mipMap = (2*(distance(texCoords,vec2(0.5,0.5)))) * 7;

		float NdotL = max(dot(N, L), 0.0);
	    float NdotH = max(dot(N, H), 0.0);
	    float NdotV = max(dot(N, V), 0.0);
    	float VdotH = max(dot(V, H), 0.0);
    	
		// irradiance factor at point
		float factor = NdotL / ( 2.0 * 3.14159265 );
		// frag color
		vec3 diffuse = 2*lightDiffuse * factor;
		
		float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	
		float alpha = acos(NdotH);
		// GGX
		//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
		float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
	    
	    // Schlick
		float F0 = 0.02;
		// Specular in the range of 0.02 - 0.2
		// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
		float glossiness = (1-roughness);
		float maxSpecular = mix(0.2, 1.0, metallic);
		F0 = max(F0, (glossiness*maxSpecular));
		//F0 = max(F0, metallic*0.2);
	    float fresnel = 1; fresnel -= dot(V, H);
		fresnel = pow(fresnel, 5.0);
		float temp = 1.0; temp -= F0;
		fresnel *= temp;
		float F = fresnel + F0;
        
		//diffuse = diffuse * (1-fresnel);
        
        float specular = clamp(F*D*G/(4*(NdotL*NdotV)), 0.0, 1.0);
        
		return diffuse * diffuseColor + diffuse * specularColor * specular * clamp(length(overheadVec2), 0.0, 1.0);
	}
}
vec3 cookTorrancePointLight(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDiffuse,
							   vec3 lightPosition, float lightRadius, float dist, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-position);
	V = ViewVector;
 	vec3 L = normalize(lightPosition-position);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);
	
	float alpha = acos(NdotH);
	float alphaSquare = alpha*alpha;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
    
    // Schlick
	float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	diff = (diff.rgb) * diffuseColor;
	

	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	float attenuation = calculateAttenuation(dist, lightRadius);
	
	return attenuation*(diff + lightDiffuse.rgb * specularColor * cookTorrance);
}


void main()
{
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	
	vec2 UV = texCoord;
	vec4 color = vec4(materialDiffuseColor, 1);
    if(hasDiffuseMap) {
    	vec2 UV;
		UV.x = texCoord.x * diffuseMapWidth;
		UV.y = texCoord.y * diffuseMapHeight;
		color = texture2D(diffuseMap, UV);
    }

	float depth = (position_clip.z / position_clip.w);
    //out_color = vec4(color.rgb, depth);
    out_color.a = 1;
    vec3 diffuseColor = mix(color.rgb, vec3(0,0,0), metallic); // biased, since specular term is only valid at POI of the probe...mäh
    vec3 specularColor = mix(vec3(0.04,0.04,0.04), color.rgb, metallic);
    
	vec3 PN_world = normalize(normal_world);
    if(hasNormalMap) {
    	vec2 UV;
		UV.x = texCoord.x * normalMapWidth;
		UV.y = texCoord.y * normalMapHeight;
		PN_world = normalize(perturb_normal(PN_world, V, UV));
    }
	
	/////////////////// SHADOWMAP
	vec3 visibility = vec3(1,1,1);
	vec4 positionShadow = (shadowMatrix * vec4(position_world.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = chebyshevUpperBound(depthInLightSpace, positionShadow);
	/////////////////// SHADOWMAP
	
	vec3 lightDiffuseSpecular = cookTorrance(V, position_world.xyz, PN_world.xyz, roughness, metallic, diffuseColor, specularColor);

	// since probes are used for ambient lighting, but don't receive ambient, they have to be biased with some ambient light
	/*float quarterAmbientStrength = 0.05;
	out_color.rgb = quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(-lightDirection.x,-lightDirection.y,lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(lightDirection.x,-lightDirection.y,lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(lightDirection.x,lightDirection.y,-lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * color.rgb * clamp(dot(lightDirection, PN_world), 0.0, 1.0);*/

	vec3 directLight = diffuseColor.rgb * lightDiffuseSpecular.rgb * visibility;
	
	out_color.rgb = directLight.rgb;
	
	for(int i = 0; i < max(pointLightMaxCount, activePointLightCount); i++) {
		
		float dist = distance(position_world.xyz, pointLightPositions[i]);
		if(dist > pointLightRadiuses[i]) { continue; }
		
		out_color.rgb += cookTorrancePointLight(-V, position_world.xyz, PN_world.xyz, roughness, metallic, pointLightColors[i], pointLightPositions[i], pointLightRadiuses[i], dist, diffuseColor, specularColor);
	}
	
	for(int i = 0; i < max(areaLightMaxCount, activeAreaLightCount); i++) {
		
		out_color.rgb += cookTorranceAreaLight(-V, position_world.xyz, PN_world.xyz, roughness, metallic, areaLightColors[i], areaLightPositions[i], i, diffuseColor, specularColor);
	}
	
	out_color.rgb += 0.0125 * color.rgb;
	
	//out_color.rgb = PN_world;
	//out_color.rgb = vec3(metallic,metallic,metallic);
}