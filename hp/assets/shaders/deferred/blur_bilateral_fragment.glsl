#version 420

layout(binding=0) uniform sampler2D renderedTexture;
uniform float blurDistance = 0.0025;
uniform float scaleX = 1;
uniform float scaleY = 1;

in vec2 pass_TextureCoord;

out vec4 out_color;

const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0 };

vec2 offsets[9] = { vec2(-blurDistance, -blurDistance),
					vec2(0, -blurDistance),
					vec2(blurDistance, -blurDistance),
					vec2(-blurDistance, 0),
					vec2(0, 0),
					vec2(blurDistance, 0),
					vec2(-blurDistance, blurDistance),
					vec2(0, blurDistance),
					vec2(blurDistance, blurDistance) };

vec4 bilateralBlur(sampler2D sampler, vec2 texCoords) {

	vec4 result = vec4(0,0,0,0);
	float normalization = 1;
	
	vec4 centerSample = textureLod(renderedTexture, texCoords + offsets[4], 0);
	result += kernel[4] * centerSample;
	
	for(int i = 0; i < 9; i++) {
		if(i == 4) { continue; }
		
		vec4 currentSample = textureLod(renderedTexture, texCoords + offsets[i], 0);
		float closeness = distance(currentSample.rgb, centerSample.rgb) / length(vec3(1,1,1));
		float sampleWeight = kernel[i] * closeness;
		result += sampleWeight * currentSample;
		normalization += sampleWeight;
	}
	
	return result + normalization * centerSample;
}

void main()
{
	vec2 texCoord = pass_TextureCoord * vec2(scaleX, scaleY);
	//vec4 in_color = textureLod(renderedTexture, texCoord, 0);
    vec4 in_color = bilateralBlur(renderedTexture, texCoord);
    out_color = in_color;
}