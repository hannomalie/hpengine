#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 in_position;
in vec3 in_Normal;

out vec4 pass_Position;
out vec3 normal_world;

void main()
{
	pass_Position = projectionMatrix * viewMatrix * modelMatrix * in_position;
    gl_Position = pass_Position;
	normal_world = (modelMatrix * viewMatrix * vec4(in_Normal,1)).xyz;
}