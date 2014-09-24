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
  //float atten_factor = -log (min (1.0, distDivRadius));
  //return vec4(atten_factor,atten_factor,atten_factor,atten_factor);
  return vec4((vec4(lightDiffuse,1) * dot_prod * atten_factor).xyz, specular_factor * atten_factor);
}
vec4 cookTorrance(in vec3 position, in vec3 normal, in vec4 color, in vec4 specular, vec3 probeColor, float roughness) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	vec3 light_position_eye = (viewMatrix * vec4(lightPosition, 1)).xyz;
    vec3 dist_to_light_eye = light_position_eye - position;
	float dist = length (dist_to_light_eye);
    if(dist > lightRadius) {discard;}
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
 	vec3 L = normalize(light_position_eye - position);
    vec3 H = normalize(L + V);
    vec3 N = normal;
    vec3 P = position;
    float NdotH = dot(N, H);
    float NdotV = dot(N, V);
    float NdotL = dot(N, L);
    float VdotH = dot(V, H);
    
    // Schlick
    float base = 1; base -= dot(V, H);
	float exponential = pow(base, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float F0 = 0.04;
	float temp = 1.0; temp -= exponential;
	float F = exponential; F += F0 * temp;
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
	
	float alpha = acos(NdotH);
	float gaussConstant = 100.0;
	float m = 1-roughness;
	float D = gaussConstant*exp(-(alpha*alpha)/(m*m));
	
	return atten_factor*vec4(vec3(lightDiffuse.rgb), specular.r * (F/3.1416) * (D*G/(NdotL/NdotV)));
	//return vec4(lightDiffuse.rgb + specular.rgb * (F/3.1416) * (D*G/(NdotL/NdotV)), 0);
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
	float roughness = texture2D(diffuseMap, st).w;
	
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
	vec4 finalColor = cookTorrance(positionView, normalView, vec4(color,1), specular, probeColor, roughness);
	
	out_DiffuseSpecular = finalColor;
}
