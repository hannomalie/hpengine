layout(binding=0) uniform sampler2D sourceTexture;
layout(binding = 1, rgba16f) uniform image2D targetImage;
#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int width = 1;
uniform int height = 1;
uniform int mipmapSource = 0;
uniform int mipmapTarget = 1;

void main(){

	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
	vec2 st = vec2(pixelPos) / vec2(width, height);

	vec4 total = vec4(0);
	const int FILTER_RADIUS = 3;

	vec3 minValue = vec3(999999);
	vec3 maxValue = vec3(-9999999);
	for(int x = -FILTER_RADIUS; x <= FILTER_RADIUS; x++){
	    for(int y = -FILTER_RADIUS; y <= FILTER_RADIUS; y++){
            vec2 positionToSample = pixelPos + ivec2(x, y);

            for(int i = mipmapSource; i > mipmapTarget; i--) {
                positionToSample *= 0.5;
            }
	        vec2 currentSt = vec2(positionToSample) / vec2(width, height);
            vec3 current = textureLod(sourceTexture, currentSt, mipmapSource).xyz;
            maxValue = max(maxValue, current);
            minValue = min(minValue, current);
		}
	}

    float halfLength = length(maxValue - minValue)/2;
    vec3 center = minValue + vec3(halfLength);
    vec4 positionRadius = vec4(center, halfLength);

	imageStore(targetImage, pixelPos, positionRadius);
}
