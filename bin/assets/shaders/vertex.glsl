#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

out vec4 pass_Color;
out vec2 pass_TextureCoord;
out vec3 pass_Normal;
out vec3 pass_Up;

void main(void) {
	gl_Position = in_Position;
	// Override gl_Position with our new calculated position
	gl_Position = projectionMatrix * viewMatrix * modelMatrix * in_Position;
	
	pass_Color = in_Color;
	pass_TextureCoord = in_TextureCoord;
	
	pass_Normal =  in_Normal;
	
	pass_Up = vec3(viewMatrix[1][0], viewMatrix[1][1], viewMatrix[1][2]);
}