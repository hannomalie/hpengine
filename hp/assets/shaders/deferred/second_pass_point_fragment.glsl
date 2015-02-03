#version 420

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
uniform float lightRadius;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec4 position_clip;
in vec4 position_view;

struct Pointlight {
	vec3 _position;
	float _radius;
	vec3 _diffuse;
	vec3 _specular;
};
//uniform int lightCount;
uniform int currentLightIndex;
layout(std140) uniform pointlights {
	Pointlight lights[1000];
};

out vec4 out_DiffuseSpecular;
out vec4 out_AOReflection;

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

#define kPI 3.1415926536f
const float pointLightRadius = 10.0;

vec3 decodeNormal(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

float calculateAttenuation(float dist) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}


float chiGGX(float v)
{
    return v > 0 ? 1 : 0;
}

float GGX_PartialGeometryTerm(vec3 v, vec3 n, vec3 h, float alpha)
{
    float VoH2 = clamp(dot(v,h), 0, 1);
    float chi = chiGGX( VoH2 / clamp(dot(v,n), 0, 1) );
    VoH2 = VoH2 * VoH2;
    float tan2 = ( 1 - VoH2 ) / VoH2;
    return (chi * 2) / ( 1 + sqrt( 1 + alpha * alpha * tan2 ) );
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = ViewVector;
	vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
	float distTemp = length (light_position_eye - position);
	
	// make every pointlight a sphere light for better highlights, thanks Unreal Engine
 	//vec3 r = reflect(V, normal);
    //vec3 centerToRay = (light_position_eye - position) * clamp(dot((light_position_eye - position), r),0,1) * r;
    //light_position_eye = (light_position_eye - position) + centerToRay*clamp(pointLightRadius/length(centerToRay),0,1);
    float pointLightSphereRadius = length(light_position_eye - position)/15;
    if(distTemp > pointLightSphereRadius) {
    	light_position_eye += normalize(position - light_position_eye) * pointLightSphereRadius;
    }
    
    
    vec3 dist_to_light_eye = light_position_eye - position;
	float dist = length(dist_to_light_eye);
    
	if(dist > lightRadius) {discard;}
    
    float atten_factor = calculateAttenuation(dist);
    if(distTemp < pointLightSphereRadius) {
    	atten_factor = 1;
    }
    
 	vec3 L = normalize(light_position_eye - position);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = max(dot(N, H), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float VdotH = max(dot(V, H), 0.0);
	vec3 halfVector = normalize(H + V);
    
    
	float alpha = acos(NdotH);
	alpha = roughness*roughness;
	// adjust distribution for sphere light
	alpha = clamp(alpha+(pointLightRadius/(3*distTemp)),0,1);
	
    float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	//G = GGX_PartialGeometryTerm(V, N, halfVector, alpha) * GGX_PartialGeometryTerm(-L, N, halfVector, alpha);
	
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
	
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	vec3 diff = diffuseColor * lightDiffuse.rgb * NdotL;
	//diff = diff * (1-fresnel);
	
	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	
	return atten_factor * (diff + cookTorrance * lightDiffuse.rgb * specularColor);
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
  	vec3 specularColor = mix(vec3(0.04,0.04,0.04), color, metallic);
  	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), vec3(1.0,1.0,1.0), metallic);
	specularColor = max(specularColor, (glossiness*maxSpecular));
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));
	
  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	
	//skip background
	if (positionView.z > -0.0001) {
		discard;
	}
	
	vec3 normalView = textureLod(normalMap, st, 0).xyz;
    //normalView = decodeNormal(normalView.xy);
	
	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;
	//vec4 finalColor = vec4(albedo,1) * vec4(phong(position.xyz, normalize(normal).xyz), 1);
	//vec4 finalColor = phong(positionView, normalView, vec4(color,1), specular);
	vec3 finalColor = cookTorrance(V, positionView, normalView, roughness, metallic, diffuseColor, specularColor);
	
	out_DiffuseSpecular.rgb = finalColor;
	out_AOReflection = vec4(0,0,0,0);
}
