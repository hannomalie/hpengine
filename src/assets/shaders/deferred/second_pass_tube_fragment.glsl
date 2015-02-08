
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightPosition;
uniform vec3 lightStart;
uniform vec3 lightEnd;
uniform vec3 lightOuterLeft;
uniform vec3 lightOuterRight;
uniform float lightRadius;
uniform float lightLength;
uniform vec3 lightDiffuse;

in vec4 position_clip;
in vec4 position_view;
in vec4 position_world;

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

float calculateAttenuation(float minDistance) {
	return 1-(minDistance/(lightRadius));
}

float calculateEndOfTubeFactor(vec3 light_position_eye, vec3 position) {
	float distToCenter = distance(light_position_eye, position);
	
	float overhead = distToCenter - (lightLength/2) + lightRadius;
	
	float attenuation = 1-clamp(overhead / lightRadius,0,1);
	
	return pow(attenuation,2);
}


vec4 calculateClosestPointAndAttenuation(vec3 light_position_eye, vec3 light_start_eye, vec3 light_end_eye, vec3 position) {
	vec3 x1 = light_start_eye;
	vec3 x2 = light_end_eye;
	vec3 x0 = position;
	
	float d = length(cross((x0-x1),(x0-x2))) / length(x2-x1);
	float dAttenuation = calculateAttenuation(d);
	
	vec3 tubeVector = light_end_eye - light_start_eye;
	vec3 perp = cross(tubeVector,tubeVector);
	
	float attenuation = min(calculateEndOfTubeFactor(light_position_eye, position), dAttenuation);
	attenuation = calculateEndOfTubeFactor(light_position_eye, position) * dAttenuation;
	vec3 closestPoint = x0 + attenuation*perp;
	closestPoint = light_start_eye + dot(position-light_start_eye, normalize(tubeVector))*normalize(tubeVector);
	
	return vec4(closestPoint, clamp(attenuation,0,1));	
}

float evaluateCookTorranceForLightPosition(vec3 V, vec3 light_position_eye, vec3 position, vec3 normal, float roughness) {
 	vec3 L = normalize(light_position_eye - position);
    vec3 H = normalize(L + V);
    vec3 N = normal;
    vec3 P = position;
    float NdotH = max(dot(N, H), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float VdotH = max(dot(V, H), 0.0);
    
    float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	
	float alpha = acos(NdotH);
	//http://www.crytek.com/download/2014_03_25_CRYENGINE_GDC_Schultz.pdf
	//alpha = pow(roughness*0.7, 6);
	float gaussConstant = 1.0;
	float m = roughness;
	float D = gaussConstant*exp(-(alpha*alpha)/(m*m));
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	D = (alpha*alpha)/(3.1415*pow((NdotH*NdotH*(alpha*alpha-1))+1, 2));
    
    // Schlick
	float F0 = 0.04;
	F0 = max(F0, ((1-roughness)/2));
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	return (F*D*G/(NdotL*NdotV));
}

float evaluateDiffuseFactor(vec3 light_position_eye, vec3 position, vec3 normal) {
	vec3 L = normalize(light_position_eye - position);
    vec3 P = position;
    vec3 N = normal;
    float NdotL = max(dot(N, L), 0.0);
    
    return NdotL;
}

vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = -normalize(position);
	//vec3 V = -ViewVector;
	vec3 light_start_eye = (viewMatrix * vec4(lightStart, 1)).xyz;
	vec3 light_end_eye = (viewMatrix * vec4(lightEnd, 1)).xyz;
	
    vec3 light_position_eye = (viewMatrix *vec4(lightPosition,1)).xyz;
	
	vec4 closestPointAttenuation = calculateClosestPointAndAttenuation(light_position_eye, light_start_eye, light_end_eye, position);
    float atten_factor = closestPointAttenuation.a;
    if(atten_factor <= 0) {discard;}
    
    //float blender = (distance(lightOuterLeft, closestPointAttenuation.rgb)/lightLength);
    //float specularAtStart = evaluateCookTorranceForLightPosition(V, light_start_eye, position, normal, roughness);
    //float specularAtEnd = evaluateCookTorranceForLightPosition(V, light_end_eye, position, normal, roughness);
	//float specularResult = mix(specularAtStart, specularAtEnd, blender);
	float specularResult = evaluateCookTorranceForLightPosition(V, closestPointAttenuation.xyz, position, normal, roughness);
	
	//float diffuseFactorAtStart = evaluateDiffuseFactor(light_start_eye, position, normal);
	//float diffuseFactorAtEnd = evaluateDiffuseFactor(light_end_eye, position, normal);
	//vec3 diff = mix(diffuseFactorAtStart, diffuseFactorAtEnd, blender) * lightDiffuse.rgb;
	vec3 diff = evaluateDiffuseFactor(closestPointAttenuation.xyz, position, normal) * lightDiffuse.rgb;
	
	float F0 = 0.04;
	F0 = max(F0, ((1-roughness)/2));
	//diff = (diff.rgb/3.1416) * (1-F0);
    
    light_position_eye = closestPointAttenuation.xyz;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	float specularAdjust = length(lightDiffuse.rgb)/length(vec3(1,1,1));
	
	specularResult = clamp(specularAdjust*specularResult,0,1);
	//return vec4(specularResult,specularResult,specularResult,specularResult);
	return atten_factor* vec4((diff), specularResult);
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
	vec4 finalColor = cookTorrance(V, positionView, normalView, roughness);
	
	out_DiffuseSpecular = finalColor;
	//out_DiffuseSpecular.rgba = vec4(roughness,roughness,roughness,roughness);
	out_AOReflection = vec4(0,0,0,0);
}
