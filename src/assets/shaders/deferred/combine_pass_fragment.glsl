#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D lightAccumulationMap;

out vec4 out_color;

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / 1280.0;
  	st.t = gl_FragCoord.y / 720.0;
  	
  	vec3 color = texture2D(diffuseMap, st).xyz;
	vec3 light = texture2D(lightAccumulationMap, st).xyz;
	
	out_color = vec4(color*light , 1);
  	
}
