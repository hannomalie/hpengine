
uniform sampler2D renderedTexture;
uniform vec3 diffuseColor = vec3(1,1,0);

in vec2 pass_TextureCoord;

layout(location=0)out vec4 out_color;

void main()
{
	//vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 1);
    
    out_color = vec4(diffuseColor,1);
}
