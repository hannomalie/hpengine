
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;
uniform int mipMapLevel;

void main() {
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, mipMapLevel);

    out_color = vec4(in_color.rgb,1);
}