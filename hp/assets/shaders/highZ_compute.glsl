layout(binding = 0) uniform sampler2D sourceTexture;
layout(binding = 1, rgba16f) uniform image2D targetImage;
#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 0;
uniform int height = 0;
uniform int mipmapSource = 0;

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000; // TODO: USE THE CAMERAS VALUES AND NOT HARDCODED !!!!11!
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

vec4 getMin(sampler2D sampler, ivec2 baseCoords, int mipLevelToSampleFrom) {
	vec4 fineZ;
	fineZ.x = (texelFetch(sampler, baseCoords + ivec2(0,0), mipLevelToSampleFrom).a);
	fineZ.y = (texelFetch(sampler, baseCoords + ivec2(0,-1), mipLevelToSampleFrom).a);
	fineZ.z = (texelFetch(sampler, baseCoords + ivec2(-1,0), mipLevelToSampleFrom).a);
	fineZ.w = (texelFetch(sampler, baseCoords + ivec2(-1,-1), mipLevelToSampleFrom).a);

	return fineZ;
}

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);

    vec4 total = getMin(sourceTexture, 2*pixelPos, mipmapSource);
    float minimum = min(total.x, min(total.y, min(total.z, total.w)));

	imageStore(targetImage, pixelPos, vec4(0,0,0,minimum));
//	imageStore(targetImage, pixelPos, vec4(1,0,0,0));
}