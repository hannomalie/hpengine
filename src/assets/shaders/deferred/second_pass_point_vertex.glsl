#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightPosition;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec4 in_Position;

out vec4 position_clip;

void main(void) {
	gl_Position = projectionMatrix * viewMatrix * (in_Position + vec4(lightPosition,1));
}