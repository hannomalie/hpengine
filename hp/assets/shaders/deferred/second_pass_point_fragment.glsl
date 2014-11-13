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

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

#define kPI 3.1415926536f
vec3 decodeNormal(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

vec4 phong (in vec3 position, in vec3 normal, in vec4 color, in vec4 specular, vec3 probeColor, float roughness) {
//////////////////////
  //Pointlight currentLight = lights[currentLightIndex];
  //vec3 lightPosition = currentLight._position;
  //vec3 lightDiffuse = currentLight._diffuse;
  //vec3 lightSpecular = currentLight._specular;
  //float lightRadius = currentLight._specular;
//////////////////////
  vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
  vec3 dist_to_light_eye = light_position_eye - position;
  vec3 direction_to_light_eye = normalize (dist_to_light_eye);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  normal), 0.0);
  
  // standard specular light
  vec3 reflection_eye = reflect (-(vec4(direction_to_light_eye, 0)).xyz, (vec4(normal, 0)).xyz);
  vec3 surface_to_viewer_eye = normalize(-position);//normalize (-(viewMatrix * vec4(normal, 0)).xyz);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  int specularPower = int(2048 * (1-roughness) + 1); //specular.a
  float specular_factor = clamp(pow (dot_prod_specular, (specularPower)), 0, 1);
  
  // attenuation (fade out to sphere edges)
  float dist = length (dist_to_light_eye);
  float distDivRadius = (dist / lightRadius);
  if(dist > lightRadius) {discard;}
  float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
  atten_factor = pow(atten_factor, 2);
  //float atten_factor = -log (min (1.0, distDivRadius));
  //return vec4(atten_factor,atten_factor,atten_factor,atten_factor);
  return vec4((vec4(lightDiffuse,1) * dot_prod * atten_factor).xyz, specular_factor * atten_factor);
}

float calculateAttenuation(float dist) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}

vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = ViewVector;
	vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
    vec3 dist_to_light_eye = light_position_eye - position;
	float dist = length (dist_to_light_eye);
    
	if(dist > lightRadius) {discard;}
    
    float atten_factor = calculateAttenuation(dist);
    
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
    float F0 = 0.02;
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
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	//diff = (diff.rgb/3.1416) * (1-F0);
	float specularAdjust = length(lightDiffuse.rgb)/length(vec3(1,1,1));
	
	return atten_factor* vec4((diff), specularAdjust*(F*D*G/(4*(NdotL*NdotV))));
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
	
	vec3 normalView = textureLod(normalMap, st, 0).xyz;
    //normalView = decodeNormal(normalView.xy);
	
	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;
	//vec4 finalColor = vec4(albedo,1) * vec4(phong(position.xyz, normalize(normal).xyz), 1);
	//vec4 finalColor = phong(positionView, normalView, vec4(color,1), specular);
	vec4 finalColor = cookTorrance(V, positionView, normalView, roughness, metallic);
	
	out_DiffuseSpecular = finalColor;
	out_AOReflection = vec4(0,0,0,0);
}
