layout(binding = 0) uniform sampler2D sourceTexture;
layout(binding = 1, r32f) uniform image2D targetImage;
layout(binding = 2) uniform sampler2D baseDepthTexture;
#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 0;
uniform int height = 0;
uniform int lastWidth = 0;
uniform int lastHeight = 0;
uniform int mipmapTarget = 0;

float getMaxR(sampler2D sampler, ivec2 baseCoords, vec2 texCoords, vec2 texelSize, int mipLevelToSampleFrom) {
	vec4 fineZ;
	fineZ.x = (texelFetch(sampler, baseCoords, mipLevelToSampleFrom).r);
	fineZ.y = (texelFetch(sampler, baseCoords + ivec2(1,0), mipLevelToSampleFrom).r);
	fineZ.z = (texelFetch(sampler, baseCoords + ivec2(1,1), mipLevelToSampleFrom).r);
	fineZ.w = (texelFetch(sampler, baseCoords + ivec2(0,1), mipLevelToSampleFrom).r);

//	fineZ = textureGather(sampler, texCoords, 0);
	fineZ.x = (textureLod(sampler, texCoords, mipLevelToSampleFrom).r);
	fineZ.y = (textureLod(sampler, clamp(texCoords + vec2(texelSize.x, 0.0), 0.0f, 1.0f), mipLevelToSampleFrom).r);
	fineZ.z = (textureLod(sampler, clamp(texCoords + vec2(texelSize), 0.0f, 1.0f), mipLevelToSampleFrom).r);
	fineZ.w = (textureLod(sampler, clamp(texCoords + vec2(0,texelSize.y), 0.0f, 1.0f), mipLevelToSampleFrom).r);

	return max(max(fineZ.x, fineZ.y), max(fineZ.z, fineZ.w));
}
float getMaxG(sampler2D sampler, ivec2 baseCoords, vec2 texCoords, vec2 texelSize, int mipLevelToSampleFrom) {
	vec4 fineZ;
	fineZ.x = (texelFetch(sampler, baseCoords, mipLevelToSampleFrom).g);
	fineZ.y = (texelFetch(sampler, baseCoords + ivec2(1,0), mipLevelToSampleFrom).g);
	fineZ.z = (texelFetch(sampler, baseCoords + ivec2(1,1), mipLevelToSampleFrom).g);
	fineZ.w = (texelFetch(sampler, baseCoords + ivec2(0,1), mipLevelToSampleFrom).g);

//	fineZ = textureGather(sampler, texCoords, 1);
	fineZ.x = (textureLod(sampler, texCoords, mipLevelToSampleFrom).g);
	fineZ.y = (textureLod(sampler, clamp(texCoords + vec2(texelSize.x, 0.0f), 0.0f, 1.0f), mipLevelToSampleFrom).g);
	fineZ.z = (textureLod(sampler, clamp(texCoords + vec2(texelSize), 0.0f, 1.0f), mipLevelToSampleFrom).g);
	fineZ.w = (textureLod(sampler, clamp(texCoords + vec2(0,texelSize.y), 0.0f, 1.0f), mipLevelToSampleFrom).g);

	return max(max(fineZ.x, fineZ.y), max(fineZ.z, fineZ.w));
}

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
	if(pixelPos.x > width || pixelPos.y > height) {
	    return;
	}
	ivec2 samplePos = 2*pixelPos;
	vec2 textureCoords = vec2(pixelPos)/vec2(width, height);
	int mipmapSource = mipmapTarget-1;
	vec2 texelSize = vec2(1.0f/width, 1.0f/height);

    float maximum;
    if(mipmapTarget == 0) {
#ifdef SOURCE_CHANNEL_R
        maximum = getMaxR(sourceTexture, samplePos, textureCoords, texelSize, 0);
#else
        maximum = getMaxG(sourceTexture, samplePos, textureCoords, texelSize, 0);
#endif
    } else {
        maximum = getMaxR(sourceTexture, samplePos, textureCoords, texelSize, mipmapSource);
    }

//Thank you!
//http://rastergrid.com/blog/2010/10/hierarchical-z-map-based-occlusion-culling/
//  vec3 extra;
//  // if we are reducing an odd-width texture then fetch the edge texels
//  if ( ( (lastWidth % 2) != 0 ) && ( pixelPos.x == lastWidth-3 ) ) {
//    // if both edges are odd, fetch the top-left corner texel
//    if ( ( (lastHeight % 2) != 0 ) && ( pixelPos.y == lastHeight-3 ) ) {
//      extra.z = texelFetch(sourceTexture, samplePos + ivec2( 1, 1), mipmapSource).x;
//      maximum = max( maximum, extra.z );
//    }
//    extra.x = texelFetch( sourceTexture, samplePos + ivec2( 1, 0), mipmapSource).x;
//    extra.y = texelFetch( sourceTexture, samplePos + ivec2( 1,-1), mipmapSource).x;
//    maximum = max( maximum, max( extra.x, extra.y ) );
//  } else if ( ( (lastHeight % 2) != 0 ) && ( pixelPos.y == lastHeight-3 ) ) {
//    // if we are reducing an odd-height texture then fetch the edge texels
//    extra.x = texelFetch( sourceTexture, samplePos + ivec2( 0, 1), mipmapSource).x;
//    extra.y = texelFetch( sourceTexture, samplePos + ivec2(-1, 1), mipmapSource).x;
//    maximum = max( maximum, max( extra.x, extra.y ) );
//  }

//	maximum = textureLod(baseDepthTexture, textureCoords, mipmapSource).g;
	imageStore(targetImage, pixelPos, vec4(maximum));
//	imageStore(targetImage, pixelPos, vec4(textureCoords.y));

}