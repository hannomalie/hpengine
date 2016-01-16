#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) readonly uniform image3D source;
layout(binding=1, rgba8) writeonly uniform image3D target;

#define KERNEL_SIZE_3
#ifdef KERNEL_SIZE_3
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
#else
const float kernel_3d[5][5][5] = {
   {
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.25f, 0.6, 1f, 0.6, 0.25f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f }
   },
   {
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.25f, 0.6, 1f, 0.6, 0.25f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f }
   },
   {
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.25f, 0.6, 1f, 0.6, 0.25f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f }
   },
   {
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.25f, 0.6, 1f, 0.6, 0.25f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f }
   },
   {
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.25f, 0.6, 1f, 0.6, 0.25f },
       { 0.2f, 0.3, 0.5f, 0.3, 0.2f },
       { 0.1f, 0.17, 0.25f, 0.17, 0.1f }
   }
};
#endif

void main(void) {
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);
	ivec3 loadPosition = storePos*2;

	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
	float weightSum = 0;

#ifdef KERNEL_SIZE_3
	int amplitude = 1;
	float divisor = 1;
#else
	int amplitude = 2;
	float divisor = 4;
#endif

	for(int x = -amplitude; x < amplitude; x++) {
	    for(int y = -amplitude; y < amplitude; y++) {
	        for(int z = -amplitude; z < amplitude; z++) {
	            float weight = kernel_3d[x+amplitude][y+amplitude][z+amplitude];
	            vec4 textureSample = imageLoad(source, loadPosition + ivec3(x,y,z));
                sourceValue.rgb += weight * textureSample.rgb;// * clamp(textureSample.a,0,1) + (sourceValue.rgb * 1-sourceValue.a);
//                sourceValue.rgba += weight * textureSample.rgba;// * clamp(textureSample.a,0,1) + (sourceValue.rgb * 1-sourceValue.a);
                sourceValue.a += weight*textureSample.a;
                weightSum += weight;
            }
        }
	}
	sourceValue.rgb /= weightSum-sourceValue.a;
//	sourceValue /= weightSum;
//	sourceValue /= 9;
//	sourceValue /= divisor;
//	vec4 sourceValue = imageLoad(source, loadPosition);
	imageStore(target, storePos, sourceValue);
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
