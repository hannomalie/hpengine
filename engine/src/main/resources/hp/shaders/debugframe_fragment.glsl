
layout(binding=0) uniform sampler2D renderedTexture;
uniform float factorForDebugRendering = 1;

in vec2 pass_TextureCoord;
out vec4 out_color;

void main()
{
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 0);
    out_color = vec4(factorForDebugRendering * in_color.rgb, 1);
}