#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D visibilityMap;
uniform int currentMipMapLevel = 1; // this is the miplevel we are targeting right now. the level we write to.

in vec2 pass_TextureCoord;

layout(location=2)out vec4 out_visibility;

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000; // TODO: USE THE CAMERAS VALUES AND NOT HARDCODED !!!!11!
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

float[2] getMinimumMaximumByW(sampler2D sampler, vec2 baseCoords, int mipLevelToSampleFrom) {
	float[2] minMax;
	vec4 fineZ;
	fineZ.x = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,0), mipLevelToSampleFrom).g);
	fineZ.y = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,-1), mipLevelToSampleFrom).g);
	fineZ.z = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,0), mipLevelToSampleFrom).g);
	fineZ.w = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,-1), mipLevelToSampleFrom).g);
	
	minMax[0] = min(min(fineZ.x, fineZ.y), min(fineZ.z, fineZ.w));
	
	fineZ.x = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,0), mipLevelToSampleFrom).b);
	fineZ.y = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(0,-1), mipLevelToSampleFrom).b);
	fineZ.z = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,0), mipLevelToSampleFrom).b);
	fineZ.w = linearizeDepth(textureOffset(sampler, baseCoords, ivec2(-1,-1), mipLevelToSampleFrom).b);
	
	minMax[1] = max(max(fineZ.x, fineZ.y), max(fineZ.z, fineZ.w));
	
	return minMax;
}

void main()
{
	//vec2 baseCoords = ivec2(gl_FragCoord.xy*(currentMipMapLevel+1));
	vec2 baseCoords = pass_TextureCoord*(pow(2,currentMipMapLevel));
	// when target mipmap is 1, we have to divide the tex coords by 1, because we sample the fullscreen texture
	int mipLevelToSampleFrom = currentMipMapLevel-1;
	
	//out_position = textureLod(positionMap, pass_TextureCoord, mipLevelToSampleFrom);
    //vec4[2] minMax = getMinimumMaximumByZ(positionMap, baseCoords, mipLevelToSampleFrom);
    //out_position = minMax[0];
    
    float[2] minMax = getMinimumMaximumByW(visibilityMap, baseCoords, mipLevelToSampleFrom);
    out_visibility.g = minMax[0];
    out_visibility.b = minMax[1];
    
    //out_visibility = vec4(1,0,0,1);
    //out_visibility = texture(normalMap, baseCoords, mipLevelToSampleFrom);
}