layout(binding=0) uniform sampler2D sourceTexture;
layout(binding = 1, rgba16f) uniform image2D targetImage;
#define WORK_GROUP_SIZE 16
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 0;
uniform int height = 0;

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
	vec2 st = vec2(pixelPos) / vec2(width, height);

	vec4 maxValue = vec4(0);
    vec2 positionToSample;

    for(int x = -1; x < 1; x++) {
        for(int y = -1; y < 1; y++) {
            positionToSample = pixelPos + ivec2(x, y);
            maxValue = max(maxValue, imageLoad(targetImage, ivec2(positionToSample)));
        }
    }
	imageStore(targetImage, pixelPos, maxValue);
}
