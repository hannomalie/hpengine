
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

void main()
{
	vec4 in_color = texture2D(renderedTexture, pass_TextureCoord);
    
    out_color = in_color;
}