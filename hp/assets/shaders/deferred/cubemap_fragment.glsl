#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;
uniform vec3 materialDiffuseColor = vec3(0,0,0);

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec4 position_clip;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;

layout(location=0)out vec4 out_color;

float linearizeDepth(float z)
{
  float n = 0.1; // camera z near TODO: MAKE THIS UNIFORRRRRRRMMMM
  float f = 500; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}

void main()
{
	vec2 UV = texCoord;
	vec4 color = vec4(materialDiffuseColor, 1);
	color = texture2D(diffuseMap, UV);
	
	float depth = (position_clip.z / position_clip.w);
    out_color = vec4(color.rgb, depth);
}