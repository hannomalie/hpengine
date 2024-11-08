
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;

layout(binding=8) uniform sampler2D lightTexture;
layout(binding=9) uniform sampler2D shadowMap;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;

uniform vec3 lightPosition;
uniform vec3 lightRightDirection;
uniform vec3 lightViewDirection;
uniform vec3 lightUpDirection;
uniform float lightHeight;
uniform float lightWidth;
uniform float lightRadius;
uniform float lightRange;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;
uniform bool useTexture = false;

in vec4 position_clip;
in vec4 position_view;

//struct Pointlight {
	//vec3 _position;
	//float _radius;
	//vec3 _diffuse;
	//vec3 _specular;
//};
//uniform int lightCount;
//uniform int currentLightIndex;
//layout(std140) uniform pointlights {
	//Pointlight lights[1000];
//};

out vec4 out_DiffuseSpecular;
out vec4 out_AOReflection;

#define kPI 3.1415926536f
vec3 decodeNormal(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
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

float calculateAttenuation(float dist) {
    float distDivRadius = (dist / lightRange);
    return clamp(1.0f - (distDivRadius), 0, 1);
}

float linearizeDepth(float z)
{
  float n = 0.1; // camera z near
  float f = 500; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}
const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0 };
	
vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0025);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * texture(sampler, texCoords + vec2(-blurDistance, -blurDistance));
	result += kernel[1] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[2] * texture(sampler, texCoords + vec2(blurDistance, -blurDistance));
	
	result += kernel[3] * texture(sampler, texCoords + vec2(-blurDistance));
	result += kernel[4] * texture(sampler, texCoords + vec2(0, 0));
	result += kernel[5] * texture(sampler, texCoords + vec2(blurDistance, 0));
	
	result += kernel[6] * texture(sampler, texCoords + vec2(-blurDistance, blurDistance));
	result += kernel[7] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[8] * texture(sampler, texCoords + vec2(blurDistance, blurDistance));
	
	return result;
}

float radicalInverse_VdC(uint bits) {
     bits = (bits << 16u) | (bits >> 16u);
     bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
     bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
     bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
     bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
     
     return float(bits) * 2.3283064365386963e-10; // / 0x100000000
}
vec2 hammersley2d(uint i, int N) {
	return vec2(float(i)/float(N), radicalInverse_VdC(i));
}

vec3 PCF(sampler2D sampler, vec2 texCoords, float referenceDepth, float inBlurDistance, float NdotL) {
	vec3 result = vec3(0,0,0);
	float blurDistance = clamp(inBlurDistance, 0.0, 0.002);
	const int N = 16;
	const float bias = 0.005;
	for (int i = 0; i < N; i++) {
		float texel = (texture(sampler, texCoords + (hammersley2d(i, N)-0.5)/50).x);
		result += texel < referenceDepth - bias ? 0 : 1;
	}
	return result/N;
}
float reduceLightBleeding(float p_max, float amount)
{
    return clamp((p_max-amount)/ (1.0-amount), 0.0, 1.0);
}
vec3 VSM(sampler2D sampler, vec2 texCoords, float referenceDepth, float inBlurDistance, float NdotL) {
	vec4 shadowMapSample = blur(shadowMap, texCoords, 0.00125);
	float M1 = shadowMapSample.x;
	float M2 = shadowMapSample.y;
	float M12 = M1 * M1;

	float p = 0.0;
	float lightIntensity = 1.0;
	const float u_minVariance = 0.00001;
	const float u_lightBleedingLimit = 0.1;
	if(referenceDepth >= M1)
	{
		// standard deviation
		float sigma2 = M2 - M12;

		// when standard deviation is smaller than epsilon
		if(sigma2 < u_minVariance)
		{
			sigma2 = u_minVariance;
		}

		// chebyshev inequality - upper bound on the
		// probability that fragment is occluded
		float intensity = sigma2 / (sigma2 + pow(referenceDepth - M1, 2));

		// reduce light bleeding
		lightIntensity = reduceLightBleeding(intensity, u_lightBleedingLimit);
	}

	/////////////////////////////////////////////////////////

	return vec3(lightIntensity);
}


vec3 getVisibility(float dist, vec4 ShadowCoordPostW, vec2 texCoords, float NdotL)
{
	float fadeOut = 1;
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
  	  	const float fadeRange = 0.21;
  	  	float maxOutside = max(distance(0.5, ShadowCoordPostW.x), distance(0.5, ShadowCoordPostW.y)) - 0.5;
  	  	fadeOut = 1-clamp(maxOutside/fadeRange, 0,1);
  	  	clamp(ShadowCoordPostW.x, 0, 1);
  	  	clamp(ShadowCoordPostW.y, 0, 1);
	}
	
	vec4 shadowMapSample = texture2D(shadowMap,ShadowCoordPostW.xy);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;
	
	moments = blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;

//	float bias = 0.0003;
//	if (momentsUnblurred.x + bias < dist) {
//		return vec3(0.0,0.0,0.0);
//	} else {
//		return vec3(1,1,1);
//	}
	
	{ return fadeOut*PCF(shadowMap, ShadowCoordPostW.xy, dist, 0.001, NdotL); }
//	{ return VSM(shadowMap, ShadowCoordPostW.xy, dist, 0.25, NdotL); }

	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.0012);

	float d = dist - moments.x;
	float p_max = (variance / (variance + d*d));
	
	//p_max = linstep(0.2, 1.0, p_max);

	float darknessFactor = 320.0;
	p_max = clamp(exp(-darknessFactor * (dist - moments.x)), 0.0, 1.0);

	return vec3(p_max,p_max,p_max);
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {

//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = ViewVector;
	//V = ViewVector;
	vec3 light_position = (vec4(lightPosition, 1)).xyz;
	vec3 light_view_direction = (vec4(lightViewDirection, 0)).xyz;
	vec3 light_up_direction = (vec4(lightUpDirection, 0)).xyz;
	vec3 light_right_direction = (vec4(lightRightDirection, 0)).xyz;
	
	vec3 lVector[ 4 ];
	vec3 leftUpper = light_position + (lightWidth/2)*(-light_right_direction) + (lightHeight/2)*(light_up_direction);
	vec3 rightUpper = light_position + (lightWidth/2)*(light_right_direction) + (lightHeight/2)*(light_up_direction);
	vec3 leftBottom = light_position + (lightWidth/2)*(-light_right_direction) + (lightHeight/2)*(-light_up_direction);
	vec3 rightBottom = light_position + (lightWidth/2)*(light_right_direction) + (lightHeight/2)*(-light_up_direction);
	
	lVector[0] = normalize(leftUpper - position);
	lVector[1] = normalize(rightUpper - position);
	lVector[3] = normalize(leftBottom - position);
	lVector[2] = normalize(rightBottom - position); // clockwise oriented all the lights rectangle points
	float tmp = dot( lVector[ 0 ], cross( ( leftBottom - leftUpper ).xyz, ( rightUpper - leftUpper ).xyz ) );
	if ( tmp < 0.0 ) {
		discard;
	} else {
		vec3 lightVec = vec3( 0.0 );
		for( int i = 0; i < 4; i ++ ) {
	
			vec3 v0 = lVector[ i ];
			vec3 v1 = lVector[ int( mod( float( i + 1 ), float( 4 ) ) ) ]; // ugh...
			lightVec += acos( dot( v0, v1 ) ) * normalize( cross( v0, v1 ) );
	
		}

	 	vec3 L = -lightVec;
	    vec3 H = normalize(L + V);
    	vec3 N = normal;
	    vec3 P = position;
        vec3 R = reflect(V, N);
        vec3 E = linePlaneIntersect(position, R, light_position, light_view_direction);

		float width = lightWidth;
	    float height = lightHeight;
	    vec3 projection = projectOnPlane(position, light_position, light_view_direction);
	    vec3 dir = projection-light_position;
	    vec2 diagonal = vec2(dot(dir,light_right_direction),dot(dir,light_up_direction));
	    vec2 nearest2D = vec2(clamp(diagonal.x, -width, width),clamp(diagonal.y, -height, height));
	    
	    // this is the amount of space the projected point is away from the border of the area light.
	    // if the term is positive, the trace is besides the broder. Up to 5 units linear fading the specular, so that
	    // we have glossy specular on rough surfaces
	    vec2 overheadVec2 = (vec2(abs(diagonal.x)-width, abs(diagonal.y)-height) / 5);
	    float overhead = clamp(max(overheadVec2.x, overheadVec2.y), 0.0, 1.0);
	    
	    vec3 nearestPointInside = light_position + (light_right_direction * nearest2D.x + light_up_direction * nearest2D.y);
	    //if(distance(P, nearestPointInside) > lightRange) { discard; }
	    vec2 texCoords = nearest2D / vec2(width, height);
        texCoords += 1;
	    texCoords /=2;
        float mipMap = (2*(distance(texCoords,vec2(0.5,0.5)))) * 7; // TODO: No hardcoded values!
        mipMap = max(0, mipMap*roughness * 7);

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
        
        if(useTexture) 
        {
        	diffuse *= textureLod(lightTexture, texCoords, mipMap).rgb;
        }
        
        
    	vec3 positionWorld = (vec4(position.xyz, 1)).xyz;
		vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
	  	positionShadow.xyz /= positionShadow.w;
	  	float depthInLightSpace = positionShadow.z;
	    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	    vec2 shadowMapTexCoords = nearest2D / vec2(512, 512); // TODO: NO HARDCODED VALUES, DAMMIT
        shadowMapTexCoords += 1;
	    shadowMapTexCoords /=2;

		vec3 visibility = getVisibility(depthInLightSpace, positionShadow, texCoords, NdotL);

//        return visibility;

        float specular = clamp(F*D*G/(4*(NdotL*NdotV)), 0.0, 1.0);
        
		return visibility * (diffuse * diffuseColor + diffuse * specularColor * specular * clamp(length(overheadVec2), 0.0, 1.0));
	}
}

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	vec3 positionWorld = (inverse(viewMatrix) * vec4(texture2D(positionMap, st).xyz, 1)).xyz;
	vec3 color = texture2D(diffuseMap, st).xyz;
	vec4 probeColorDepth = texture2D(probe, st);
	vec3 probeColor = probeColorDepth.rgb;
	float roughness = texture2D(positionMap, st).w;
	float metallic = texture2D(diffuseMap, st).w;
  	vec3 specularColor = mix(vec3(0.04,0.04,0.04), color, metallic);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));
	
  	vec4 position_clip_post_w = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	
	//skip background
//	if (positionWorld.z > -0.0001) {
//	  discard;
//	}
	
	vec3 normalWorld = texture2D(normalMap, st).xyz;
    //normalView = decodeNormal(normalView.xy);
	
	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;
	//vec4 finalColor = vec4(albedo,1) * vec4(phong(position.xyz, normalize(normal).xyz), 1);
	//vec4 finalColor = phong(positionView, normalView, vec4(color,1), specular);
	vec3 finalColor = cookTorrance(V, positionWorld, normalWorld, roughness, metallic, diffuseColor, specularColor);
	
	out_DiffuseSpecular.rgb = 4 * finalColor;
	out_AOReflection = vec4(0,0,0,0);
}
