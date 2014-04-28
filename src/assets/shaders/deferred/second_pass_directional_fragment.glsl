#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;

const vec3 kd = vec3 (1.0, 1.0, 1.0) * 0.2;
const vec3 ks = vec3 (1.0, 1.0, 1.0);
const float specular_exponent = 100.0;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec2 pass_TextureCoord;
out vec4 out_Color;

vec3 phong (in vec3 p_eye, in vec3 n_eye) {
  vec3 direction_to_light_eye = normalize((viewMatrix * vec4(lightDirection, 0)).xyz);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  n_eye), 0.0);
  vec3 Id = lightDiffuse * kd * dot_prod; // final diffuse intensity
  
  // standard specular light
  vec3 reflection_eye = reflect (-direction_to_light_eye, n_eye);
  vec3 surface_to_viewer_eye = normalize (-p_eye);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  float specular_factor = pow (dot_prod_specular, specular_exponent);
  vec3 Is = lightSpecular * ks * specular_factor; // final specular intensity
  
  // attenuation (fade out to sphere edges)
  float dist_2d = 1;//distance (light_position_eye, p_eye);
  float atten_factor = -log (min (1.0, dist_2d / 5.0));
  
  return vec3(dot_prod,dot_prod,dot_prod);
  //return (Id + Is);// * atten_factor;
  //return vec3(dot_prod,dot_prod,dot_prod);
}
void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  
	vec3 position = texture2D(positionMap, st).xyz;
	vec3 albedo = texture2D(diffuseMap, st).xyz;
	
	// skip background
	if (position.z > -0.0001) {
	  discard;
	}
	vec3 normal = texture2D(normalMap, st).xyz;
	//vec4 finalColor = vec4(albedo,1) * ( vec4(phong(position.xyz, normalize(normal).xyz), 1));
	vec4 finalColor = vec4(phong(position.xyz, normalize(normal).xyz), 1);
	out_Color = finalColor;
}
