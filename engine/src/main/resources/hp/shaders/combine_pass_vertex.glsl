
in vec4 in_Position;
in vec4 in_TexCoord;

out vec3 position;
out vec2 texCoord;

void main(void) {
	gl_Position = in_Position;
	position = in_Position.xyz;
	texCoord = in_TexCoord.xy;
}