
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

void main()
{
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 0);
    
    out_color = in_color;
}