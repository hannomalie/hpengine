uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 in_Position;
out vec2 pass_TextureCoord;

void main(void) {
//	gl_Position = in_Position;

	vec2 _position = vec2(gl_VertexID % 2, gl_VertexID / 2) * 4.0 - 1;
	gl_Position = vec4(_position, 0f, 1f);
}
