#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;

in vec4 pass_Color;
in vec2 pass_TextureCoord;
in vec3 pass_Normal;
in vec3 pass_Up;

out vec4 out_Color;

void main(void) {
	out_Color = pass_Color;
	vec4 normal = vec4(pass_Normal, 1);
	normal = vec4(texture2D(normalMap, pass_TextureCoord));
	
	out_Color = normal*vec4(texture2D(diffuseMap, pass_TextureCoord));
}