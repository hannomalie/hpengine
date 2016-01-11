#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) readonly uniform image3D source;
layout(binding=1, rgba8) writeonly uniform image3D target;

const float kernel_3d[3][3][3] = {
   {
       { 0.25f, 0.5f, 0.25f },
       { 0.5f, 0.5f, 0.5f },
       { 0.25f, 0.5f, 0.25f }
   },
   {
       { 0.25f, 0.5f, 0.25f },
       { 0.5f, 1f, 0.5f },
       { 0.25f, 0.5f, 0.25f }
   },
   {
       { 0.25f, 0.5f, 0.25f },
       { 0.5f, 0.5f, 0.5f },
       { 0.25f, 0.5f, 0.25f }
   }
};

void main(void) {
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);
	ivec3 loadPosition = storePos*2;

	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
	float weightSum = 0;
	for(int x = -1; x < 1; x++) {
	    for(int y = -1; y < 1; y++) {
	        for(int z = -1; z < 1; z++) {
	            float weight = kernel_3d[x+1][y+1][z+1];
	            vec4 textureSample = imageLoad(source, loadPosition + ivec3(x,y,z));
                sourceValue.rgb += weight * textureSample.rgb * clamp(textureSample.a,0,1);
                sourceValue.a += weight*textureSample.a;
                weightSum += weight;
            }
        }
	}
//	sourceValue /= weightSum;
//	sourceValue /= 9;
//	vec4 sourceValue = imageLoad(source, loadPosition);
	imageStore(target, storePos, sourceValue);
//	imageStore(target, storePos, vec4(1,0,0,1));
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
