in vec4 in_Position;
in vec2 in_TextureCoord;

out vec2 pass_TextureCoord;
out vec4 pass_Position;

void main()
{
//    gl_Position = in_Position;
//    pass_Position = in_Position;
//    pass_TextureCoord = in_TextureCoord;


    vec2 _position = vec2(gl_VertexID % 2, gl_VertexID / 2) * 4.0 - 1;
    vec2 _texCoord = (_position + 1) * 0.5;
    gl_Position = vec4(_position, 0.0f, 1.0f);
    pass_Position = vec4(_position, 0.0f, 1.0f);
    pass_TextureCoord = _texCoord;
}