#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) readonly uniform image3D source;
layout(binding=1, rgba8) writeonly uniform image3D target;
layout(binding=2, rgba8) writeonly uniform image3D targetTwo;

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
       { 0.5f, 1.0f, 0.5f },
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

vec4[8] fetchTexels(ivec3 pos) {
  return vec4[8] (imageLoad(source, pos + ivec3(1, 1, 1)),
                  imageLoad(source, pos + ivec3(1, 1, 0)),
                  imageLoad(source, pos + ivec3(1, 0, 1)),
                  imageLoad(source, pos + ivec3(1, 0, 0)),
                  imageLoad(source, pos + ivec3(0, 1, 1)),
                  imageLoad(source, pos + ivec3(0, 1, 0)),
                  imageLoad(source, pos + ivec3(0, 0, 1)),
                  imageLoad(source, pos + ivec3(0, 0, 0)));
}

void main(void) {
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);
	ivec3 loadPosition = storePos*2;

	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
	float weightSum = 0;

	vec4 values[8] = fetchTexels(loadPosition);
	vec4 finalValue = (values[0] + values[4]
                     + values[1] + values[5]
                     + values[2] + values[6]
                     + values[3] + values[7]) / 8;
    imageStore(target, storePos, finalValue);
//    imageStore(targetTwo, storePos, finalValue);

//	imageStore(target, storePos, sourceValue);
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
