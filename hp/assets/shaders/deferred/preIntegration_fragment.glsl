#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D visibilityMap;
int currentMipMapLevel = 1; // this is the miplevel we are targeting right now. the level we write to.

in vec2 pass_TextureCoord;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_visibility;

float getVisibility(sampler2D sampler, ivec2 baseCoords, int mipLevelToSampleFrom) {
	
	vec4 fineZ;
	fineZ.x = texelFetch(sampler, baseCoords + ivec2(0,0), mipLevelToSampleFrom).g; // g is minimum
	fineZ.y = texelFetch(sampler, baseCoords + ivec2(0,-1), mipLevelToSampleFrom).g;
	fineZ.z = texelFetch(sampler, baseCoords + ivec2(-1,0), mipLevelToSampleFrom).g;
	fineZ.w = texelFetch(sampler, baseCoords + ivec2(-1,-1), mipLevelToSampleFrom).g;
	
	float minimum = texelFetch(sampler, baseCoords + ivec2(0,0), currentMipMapLevel).g;
	float maximum = texelFetch(sampler, baseCoords + ivec2(0,0), currentMipMapLevel).b; // b is maximum
	
	float coarseVolume = 1 / (maximum - minimum);
	vec4 visibility;
	visibility.x = texelFetch(sampler, baseCoords + ivec2(0,0), mipLevelToSampleFrom).r; // r is visibility
	visibility.y = texelFetch(sampler, baseCoords + ivec2(0,-1), mipLevelToSampleFrom).r;
	visibility.z = texelFetch(sampler, baseCoords + ivec2(-1,0), mipLevelToSampleFrom).r;
	visibility.w = texelFetch(sampler, baseCoords + ivec2(-1,-1), mipLevelToSampleFrom).r;
	
	vec4 integration =  (1-clamp(fineZ - minimum, 0, 1)) * abs(coarseVolume) * visibility;
	float coarseIntegration = dot(vec4(0.25f,0.25f,0.25f,0.25f), integration);
	
	return coarseIntegration;
}

void main()
{
	ivec2 baseCoords = ivec2(gl_FragCoord.xy/(currentMipMapLevel));
	// when target mipmap is 1, we have to divide the tex coords by 1, because we sample the fullscreen texture
	int mipLevelToSampleFrom = currentMipMapLevel-1;
	
	//out_position = textureLod(positionMap, pass_TextureCoord, mipLevelToSampleFrom);
    //vec4[2] minMax = getMinimumMaximumByZ(positionMap, baseCoords, mipLevelToSampleFrom);
    //out_position = minMax[0];
    
    out_visibility.r = getVisibility(visibilityMap, baseCoords, mipLevelToSampleFrom);
}