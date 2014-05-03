#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
uniform float materialSpecularCoefficient = 0;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec2 pass_TextureCoord;
out vec4 out_DiffuseSpecular;

vec4 phong (in vec3 p_eye, in vec3 n_eye, in vec4 specular) {
  vec3 direction_to_light_eye = normalize((viewMatrix * vec4(lightDirection, 0)).xyz);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  n_eye), 0.0);
  
  // standard specular light
  vec3 reflection_eye = reflect (-direction_to_light_eye, n_eye);
  vec3 surface_to_viewer_eye = normalize (-p_eye);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  float specular_factor = pow (dot_prod_specular, specular.w);
  
  return vec4(lightDiffuse * dot_prod,0.1);
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
	vec4 specular = texture2D(specularMap, st);
	//vec4 finalColor = vec4(albedo,1) * ( vec4(phong(position.xyz, normalize(normal).xyz), 1));
	vec4 finalColor = phong(position.xyz, normalize(normal).xyz, specular);
	out_DiffuseSpecular = finalColor;
}
