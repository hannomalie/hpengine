#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

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

vec4 phong (in vec3 p_eye, in vec3 n_eye, in vec4 specular) {
//////////////////////
  //Pointlight currentLight = lights[currentLightIndex];
  //vec3 lightPosition = currentLight._position;
  //vec3 lightDiffuse = currentLight._diffuse;
  //vec3 lightSpecular = currentLight._specular;
  //float lightRadius = currentLight._specular;
//////////////////////
  vec3 light_position_eye = position_view.xyz;//(viewMatrix * vec4(lightPosition,1)).xyz;//vec3 (V * vec4 (lp, 1.0));
  vec3 dist_to_light_eye = light_position_eye - p_eye;
  vec3 direction_to_light_eye = normalize (dist_to_light_eye);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  n_eye), 0.0);
  
  // standard specular light
  vec3 reflection_eye = reflect (-direction_to_light_eye, n_eye);
  vec3 surface_to_viewer_eye = normalize (-p_eye);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  float specular_factor = pow (dot_prod_specular, specular.w);
  
  // attenuation (fade out to sphere edges)
  float dist_2d = length ((viewMatrix * vec4(lightPosition,1)).xyz - p_eye);
  float distDivRadius = (dist_2d / lightRadius);
  //if (distDivRadius > 1) {return vec3(distDivRadius,0,0);}
  //float atten_factor = 1.0 / ((1+0.22*distDivRadius)*(1+0.20*distDivRadius*distDivRadius));
  float atten_factor = clamp(1.0f - dist_2d/lightRadius, 0.0, 1.0);
  //float atten_factor = -log (min (1.0, dist_2d / lightRadius));
  //return vec3(atten_factor,atten_factor,atten_factor);
  //return (Id/* + Is*/) * atten_factor;
  return vec4(lightDiffuse*dot_prod*atten_factor, specular_factor * specular.x*atten_factor);
}
void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  
	vec3 position = texture2D(positionMap, st).xyz;
	vec3 albedo = texture2D(diffuseMap, st).xyz;
	
	//skip background
	if (position.z > -0.0001) {
	  discard;
	}
	
	vec3 normal = texture2D(normalMap, st).xyz;
	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;
	//vec4 finalColor = vec4(albedo,1) * vec4(phong(position.xyz, normalize(normal).xyz), 1);
	vec4 finalColor = phong(position.xyz, (normal).xyz, specular);
	out_DiffuseSpecular = finalColor;
}
