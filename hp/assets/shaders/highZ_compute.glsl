layout(binding = 0) uniform sampler2D sourceTexture;
layout(binding = 1, rgba16f) uniform image2D targetImage;
#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 0;
uniform int height = 0;
uniform int lastWidth = 0;
uniform int lastHeight = 0;
uniform int mipmapTarget = 0;

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000; // TODO: USE THE CAMERAS VALUES AND NOT HARDCODED !!!!11!
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

vec4 getMaxA(sampler2D sampler, ivec2 baseCoords, int mipLevelToSampleFrom) {
	vec4 fineZ;
	fineZ.x = (texelFetch(sampler, baseCoords + ivec2(0,0), mipLevelToSampleFrom).a);
	fineZ.y = (texelFetch(sampler, baseCoords + ivec2(0,-1), mipLevelToSampleFrom).a);
	fineZ.z = (texelFetch(sampler, baseCoords + ivec2(-1,0), mipLevelToSampleFrom).a);
	fineZ.w = (texelFetch(sampler, baseCoords + ivec2(-1,-1), mipLevelToSampleFrom).a);

	return fineZ;
}
vec4 getMaxG(sampler2D sampler, ivec2 baseCoords, int mipLevelToSampleFrom) {
	vec4 fineZ;
	fineZ.x = (texelFetch(sampler, baseCoords + ivec2(0,0), mipLevelToSampleFrom).g);
	fineZ.y = (texelFetch(sampler, baseCoords + ivec2(0,-1), mipLevelToSampleFrom).g);
	fineZ.z = (texelFetch(sampler, baseCoords + ivec2(-1,0), mipLevelToSampleFrom).g);
	fineZ.w = (texelFetch(sampler, baseCoords + ivec2(-1,-1), mipLevelToSampleFrom).g);

	return fineZ;
}

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 samplePos = 2*pixelPos+1;
	int mipmapSource = mipmapTarget-1;

    vec4 total;
    if(mipmapTarget == 0) {
        total = getMaxG(sourceTexture, samplePos, 0);
    } else {
        total = getMaxA(sourceTexture, samplePos, mipmapSource);
    }
    float maximum = max(total.x, max(total.y, max(total.z, total.w)));


//Thank you!
//http://rastergrid.com/blog/2010/10/hierarchical-z-map-based-occlusion-culling/
  vec3 extra;
  // if we are reducing an odd-width texture then fetch the edge texels
  if ( ( (lastWidth & 1) != 0 ) && ( samplePos.x == lastWidth-3 ) ) {
    // if both edges are odd, fetch the top-left corner texel
    if ( ( (lastHeight & 1) != 0 ) && ( samplePos.y == lastHeight-3 ) ) {
      extra.z = texelFetch(sourceTexture, samplePos + ivec2( 1, 1), mipmapSource).x;
      maximum = max( maximum, extra.z );
    }
    extra.x = texelFetch( sourceTexture, samplePos + ivec2( 1, 0), mipmapSource).x;
    extra.y = texelFetch( sourceTexture, samplePos + ivec2( 1,-1), mipmapSource).x;
    maximum = max( maximum, max( extra.x, extra.y ) );
  } else
  // if we are reducing an odd-height texture then fetch the edge texels
  if ( ( (lastHeight & 1) != 0 ) && ( samplePos.y == lastHeight-3 ) ) {
    extra.x = texelFetch( sourceTexture, samplePos + ivec2( 0, 1), mipmapSource).x;
    extra.y = texelFetch( sourceTexture, samplePos + ivec2(-1, 1), mipmapSource).x;
    maximum = max( maximum, max( extra.x, extra.y ) );
  }

//    maximum = 1;
//	imageStore(targetImage, pixelPos, vec4(imageLoad(targetImage, pixelPos).rgb,maximum));
	imageStore(targetImage, pixelPos, vec4(0,0,0,maximum));
}