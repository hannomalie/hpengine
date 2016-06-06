layout(binding=0) uniform sampler2D sourceTexture;
layout(binding = 1, rgba16f) uniform image2D targetImage;
#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 0;
uniform int height = 0;
uniform int mipmapSource = 0;
uniform int mipmapTarget = 0;

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);

	vec4 total = vec4(0);
	const int FILTER_RADIUS = 4;
	for(int i = -FILTER_RADIUS; i <= FILTER_RADIUS; i++){
#ifdef HORIZONTAL
	    vec2 positionToSample = pixelPos + ivec2(0, i);
#endif
#ifdef VERTICAL
	    vec2 positionToSample = pixelPos + ivec2(i, 0);
#endif

	    for(int i = mipmapSource; i > mipmapTarget; i--) {
	        positionToSample *= 0.5;
	    }
		total += texelFetch(sourceTexture, ivec2(positionToSample), mipmapSource);
	}

	total /= FILTER_RADIUS*2+1;

	imageStore(targetImage, pixelPos, total);
}
