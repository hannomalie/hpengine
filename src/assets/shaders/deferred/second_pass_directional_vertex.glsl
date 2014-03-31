#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec4 in_Position;
out vec2 pass_TextureCoord;

void main(void) {
	gl_Position = in_Position;
}