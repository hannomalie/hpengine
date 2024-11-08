
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;
uniform int mipMapLevel;
uniform float factorForDebugRendering;

void main() {
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, mipMapLevel);

    // TODO: Use IGNORE_ALPHA
    out_color = vec4(factorForDebugRendering * in_color.rgb,1);
}