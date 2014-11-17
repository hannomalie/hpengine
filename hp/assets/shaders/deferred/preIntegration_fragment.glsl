#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D visibilityMap;
uniform int currentMipMapLevel = 1; // this is the miplevel we are targeting right now. the level we write to.

in vec2 pass_TextureCoord;

layout(location=4)out vec4 out_visibility; // visibility

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000; // TODO: USE THE CAMERAS VALUES AND NOT HARDCODED !!!!11!
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

float getVisibility(sampler2D sampler, vec2 baseCoords, int mipLevelToSampleFrom, float minimum, float maximum) {
	
	vec4 fineZ;
	fineZ.x = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,0), mipLevelToSampleFrom).g);
	fineZ.y = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,-1), mipLevelToSampleFrom).g);
	fineZ.z = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,0), mipLevelToSampleFrom).g);
	fineZ.w = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,-1), mipLevelToSampleFrom).g);
	
	float coarseVolume = 1 / (maximum - minimum);
	vec4 visibility;
	visibility.x = (textureOffset(sampler, baseCoords, ivec2(0,0), mipLevelToSampleFrom).r); // r is visibility
	visibility.y = (textureOffset(sampler, baseCoords, ivec2(0,-1), mipLevelToSampleFrom).r);
	visibility.z = (textureOffset(sampler, baseCoords, ivec2(-1,0), mipLevelToSampleFrom).r);
	visibility.w = (textureOffset(sampler, baseCoords, ivec2(-1,-1), mipLevelToSampleFrom).r);
	
	vec4 integration = fineZ * abs(coarseVolume) * visibility;
	float coarseIntegration = dot(vec4(0.25f,0.25f,0.25f,0.25f), integration);
	
	return 0.001*abs(coarseVolume);
	return coarseIntegration;
}

void main()
{
	// when target mipmap is 1, we have to divide the tex coords by 1, because we sample the fullscreen texture
	int mipLevelToSampleFrom = currentMipMapLevel-1;
	
    out_visibility.gba = textureOffset(visibilityMap, pass_TextureCoord, ivec2(0,0), currentMipMapLevel).gba; // TODO: Do I have to output this!? wtf
    out_visibility.r = getVisibility(visibilityMap, pass_TextureCoord, mipLevelToSampleFrom, linearizeDepth(out_visibility.g), linearizeDepth(out_visibility.b));
}