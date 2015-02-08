
layout(binding=0) uniform sampler2D renderedTexture;
uniform float blurDistance = 0.0025;
uniform float scaleX = 1;
uniform float scaleY = 1;

in vec2 pass_TextureCoord;

out vec4 out_color;

const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0 };

vec4 blur(sampler2D sampler, vec2 texCoords) {
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * textureLod(renderedTexture, texCoords + vec2(-blurDistance, -blurDistance), 0);
	result += kernel[1] * textureLod(renderedTexture, texCoords + vec2(0, -blurDistance), 0);
	result += kernel[2] * textureLod(renderedTexture, texCoords + vec2(blurDistance, -blurDistance), 0);
	
	result += kernel[3] * textureLod(renderedTexture, texCoords + vec2(-blurDistance, 0), 0);
	result += kernel[4] * textureLod(renderedTexture, texCoords + vec2(0, 0), 0);
	result += kernel[5] * textureLod(renderedTexture, texCoords + vec2(blurDistance, 0), 0);
	
	result += kernel[6] * textureLod(renderedTexture, texCoords + vec2(-blurDistance, blurDistance), 0);
	result += kernel[7] * textureLod(renderedTexture, texCoords + vec2(0, -blurDistance), 0);
	result += kernel[8] * textureLod(renderedTexture, texCoords + vec2(blurDistance, blurDistance), 0);
	
	return result;
}

void main()
{
	vec2 texCoord = pass_TextureCoord * vec2(scaleX, scaleY);
	//vec4 in_color = textureLod(renderedTexture, texCoord, 0);
    vec4 in_color = blur(renderedTexture, texCoord);
    out_color = in_color;
}