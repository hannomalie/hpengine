layout(std430, binding=1) buffer _drawCount {
	uint drawCount;
};

#define WORK_GROUP_SIZE 8
layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;

uniform int maxDrawCount = 0;

void main(){
	ivec2 pixelPos = ivec2(gl_GlobalInvocationID.xy);
	if(gl_GlobalInvocationID.x < maxDrawCount)
	{
        atomicAdd(drawCount, 1);
	}
}