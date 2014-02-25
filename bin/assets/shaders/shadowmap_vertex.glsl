#version 420

in vec4 in_position;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

out vec4 pass_Position;

void main()
{
	pass_Position = projectionMatrix * viewMatrix * modelMatrix * in_position;
	//pass_Position = viewMatrix * modelMatrix * in_position;
    gl_Position = pass_Position;
}