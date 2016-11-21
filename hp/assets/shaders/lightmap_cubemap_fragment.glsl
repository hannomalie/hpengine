layout(binding=9) uniform sampler2D lightmap;

in vec3 lightmapTextureCoord;
in vec4 positionWorld;

layout(location=0)out vec4 out_color;

void main()
{
	out_color = vec4(lightmapTextureCoord.rg,0,1);
//	out_color = vec4(lightmapTextureCoord.rg*vec2(100.0),0,1);
//	out_color = vec4(positionWorld.xyz/100f,1);
//	out_color = vec4(textureLod(lightmap, lightmapTextureCoord.xy, 0).xyz,1);
}
