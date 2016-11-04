
in vec3 lightmapTextureCoord;

layout(location=0)out vec4 out_color;

void main()
{
	out_color = vec4(lightmapTextureCoord.rg,0,1);
	//out_color = vec4(1,0,0,1);
}
