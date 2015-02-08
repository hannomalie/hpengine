
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D shadowMapWorldPosition; // world position, 
layout(binding=8) uniform sampler2D shadowMapDiffuseColor; // color

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;
uniform vec3 lightDiffuse;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

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

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
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

float calculateAttenuation(float dist, float lightRange) {
    float distDivRadius = (dist / lightRange);
    return clamp(1.0f - (distDivRadius), 0, 1);
}

vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, vec3 lightPosition, vec3 lightViewDirection, vec3 lightUpDirection, vec3 lightRightDirection, float lightWidth, float lightHeight, float lightRange, float metallic) {

//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = -ViewVector;
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
	lVector[2] = normalize(rightBottom - position);
	float tmp = dot( lVector[ 0 ], cross( ( leftBottom - leftUpper ).xyz, ( rightUpper - leftUpper ).xyz ) );
	if ( tmp > 0.0 ) {
		discard;
	} else {
		vec3 lightVec = vec3( 0.0 );
		for( int i = 0; i < 4; i ++ ) {
	
			vec3 v0 = lVector[ i ];
			vec3 v1 = lVector[ int( mod( float( i + 1 ), float( 4 ) ) ) ]; // ugh...
			lightVec += acos( dot( v0, v1 ) ) * normalize( cross( v0, v1 ) );
	
		}
		
	 	vec3 L = lightVec;
	    vec3 H = normalize(L + V);
	    vec3 N = normalize(normal);
	    vec3 P = position;
        vec3 R = reflect(normalize(-position), N);
        vec3 E = linePlaneIntersect(position, R, light_position_eye, light_view_direction_eye);

		float width = lightWidth;
	    float height = lightHeight;
	    vec3 projection = projectOnPlane(position, light_position_eye, light_view_direction_eye);
	    vec3 dir = projection-light_position_eye;
	    vec2 diagonal = vec2(dot(dir,light_right_direction_eye),dot(dir,light_up_direction_eye));
	    vec2 nearest2D = vec2(clamp(diagonal.x, -width, width),clamp(diagonal.y, -height, height));
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
		vec3 lightDiffuse = vec3(1,0,1);
		vec3 diffuse = lightRange*lightDiffuse * factor;
		
		float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	
		float alpha = acos(NdotH);
		// GGX
		//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
		float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
	    
	    // Schlick
		float F0 = 0.02;
		// Specular in the range of 0.02 - 0.2
		// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
		F0 = max(F0, ((1-roughness)*0.02));
	    float fresnel = 1; fresnel -= dot(V, H);
		fresnel = pow(fresnel, 5.0);
		//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
		float temp = 1.0; temp -= F0;
		fresnel *= temp;
		float F = fresnel + F0;
        
		//diff = (diff.rgb/3.1416) * (1-F0);
        
        if(useTexture) {
        	//diffuse *= textureLod(lightTexture, texCoords, mipMap).rgb;
        }
        
		float specularAdjust = length(diffuse)/length(vec3(1,1,1));
        float specular = specularAdjust*(F*D*G/(4*(NdotL*NdotV)));
        
		return vec4(diffuse, specular);
	}
}

float calculateAttenuationPoint(float dist, float lightRadius) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}
vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, vec3 lightPosition, float lightRadius, float metallic) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = ViewVector; 
	vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
    vec3 dist_to_light_eye = light_position_eye - position;
	float dist = length (dist_to_light_eye);
    
	if(dist > lightRadius) {
		return vec4(0,0,0,0);
	}
    
    float atten_factor = calculateAttenuationPoint(dist, lightRadius);
    
 	vec3 L = normalize(light_position_eye - position);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = max(dot(N, H), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float VdotH = max(dot(V, H), 0.0);
	
    float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	
	float alpha = acos(NdotH);
	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	//alpha = roughness*roughness;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
    
    // Schlick
	float F0 = 0.04;
	// Specular in the range of 0.02 - 0.2
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	F0 = max(F0, ((1-roughness)*0.2));
	//F0 = max(F0, metallic*0.2);
	
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = lightDiffuse * NdotL;
	//diff = (diff.rgb/3.1416) * (1-F0);
	float specularAdjust = length(lightDiffuse.rgb)/length(vec3(1,1,1));
	
	return atten_factor * vec4((diff), specularAdjust*(F*D*G/(4*(NdotL*NdotV))));
	//return atten_factor* vec4((diff), 0);
}

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	st /= secondPassScale;
  
	vec3 positionView = texture2D(positionMap, st).xyz;
	vec3 color = texture2D(diffuseMap, st).xyz;
	vec4 probeColorDepth = texture2D(probe, st);
	vec3 probeColor = probeColorDepth.rgb;
	float roughness = texture2D(positionMap, st).w;
	float metallic = texture2D(diffuseMap, st).w;
	
  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	
	//skip background
	if (positionView.z > -0.0001) {
	  discard;
	}
	
	vec3 normalView = texture2D(normalMap, st).xyz;
    //normalView = decodeNormal(normalView.xy);
	
	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;
	//vec4 finalColor = vec4(albedo,1) * vec4(phong(position.xyz, normalize(normal).xyz), 1);
	//vec4 finalColor = phong(positionView, normalView, vec4(color,1), specular);
	
	vec4 finalColor = vec4(0,0,0,0);
	
	const int SAMPLES = 20;
	for(int x = 0; x < SAMPLES; x++) {
		for(int y = 0; y < SAMPLES; y++) {
		
			vec2 coords = vec2(float(x)/float(SAMPLES), float(y)/float(SAMPLES));
			vec4 shadowWorldPosition = textureLod(shadowMapWorldPosition, coords, 0);
			vec4 shadowWorldNormal = vec4(decodeNormal(texture(shadowMap, coords).ba),0);
			vec4 shadowDiffuseColor = vec4((texture(shadowMapDiffuseColor, coords).rgb), 0);
			shadowWorldPosition += shadowWorldNormal;
		
			//finalColor += cookTorrance(V, positionView, normalView, roughness, (viewMatrix * shadowWorldPosition).xyz, (viewMatrix * shadowWorldNormal).xyz, vec3(0,1,0), vec3(1,0,0), 20, 20, 150);
			finalColor += 0.5 * shadowDiffuseColor * cookTorrance(V, positionView, normalView, roughness, shadowWorldPosition.xyz, 80, metallic);
		}
	}
	
	float maxIndirectLight = 0.1;
	out_DiffuseSpecular = clamp(finalColor, vec4(0,0,0,0), vec4(maxIndirectLight,maxIndirectLight,maxIndirectLight,0));
	out_AOReflection = vec4(0,0,0,0);
}
