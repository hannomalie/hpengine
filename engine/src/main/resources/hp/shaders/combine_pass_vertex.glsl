
in vec4 in_Position;
in vec4 in_TexCoord;

out vec3 position;
out vec2 texCoord;

void main(void) {
//	gl_Position = in_Position;
//	position = in_Position.xyz;
//	texCoord = in_TexCoord.xy;
	vec2 _position = vec2(gl_VertexID % 2, gl_VertexID / 2) * 4.0 - 1;
	vec2 _texCoord = (_position + 1) * 0.5;
	gl_Position = vec4(_position, 0.0f, 1.0f);
	position = vec3(_position, 0.0f);
	texCoord = _texCoord;
}