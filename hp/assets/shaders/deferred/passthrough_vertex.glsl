
in vec4 in_Position;
in vec2 in_TextureCoord;

out vec2 pass_TextureCoord;
out vec4 pass_Position;

void main()
{
    gl_Position = in_Position;
    pass_Position = in_Position;
    //pass_TextureCoord = in_Position.xy * vec2(0.5, 0.5) + vec2(0.5, 0.5);
    pass_TextureCoord = in_TextureCoord;
}