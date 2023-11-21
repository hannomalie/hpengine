
in vec4 in_Position;
in vec2 in_TextureCoord;

out vec2 pass_TextureCoord;
out vec4 pass_Position;

void main()
{
    vec2 position = vec2(gl_VertexID % 2, gl_VertexID / 2) * 4.0f - 1;
    vec2 texCoord = (position + 1) * 0.5f;
    gl_Position = vec4(position, 0.0f, 1.0f);
    pass_Position = vec4(position, 0.0f, 1.0f);
    pass_TextureCoord = texCoord;
}