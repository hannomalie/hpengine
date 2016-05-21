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

#ifdef KERNEL_SIZE_3
    const int kernelSize = 3;
	const int amplitude = 1;
	const float divisor = 1;
#else
    const int kernelSize = 5;
	const int amplitude = 2;
	const float divisor = 4;
#endif

    vec4[kernelSize*kernelSize*kernelSize] texels;

    int counter = 0;
	for(int x = -amplitude; x < amplitude; x++) {
	    for(int y = -amplitude; y < amplitude; y++) {
	        for(int z = -amplitude; z < amplitude; z++) {
	            float weight = kernel_3d[x+amplitude][y+amplitude][z+amplitude];
	            vec4 textureSample = imageLoad(source, loadPosition + ivec3(x,y,z));
                sourceValue.rgb += weight * textureSample.rgb;// * clamp(textureSample.a,0,1) + (sourceValue.rgb * 1-sourceValue.a);
//                sourceValue.rgba += weight * textureSample.rgba;// * clamp(textureSample.a,0,1) + (sourceValue.rgb * 1-sourceValue.a);
                sourceValue.a += weight*textureSample.a;
                weightSum += weight;

                texels[counter] = textureSample;
                counter++;
            }
        }
	}

	sourceValue *= sourceValue.a;
	sourceValue /= weightSum;

	vec4 values[8] = fetchTexels(loadPosition);
        imageStore(target, storePos, (values[0] + values[4] * (1 - values[0].a)
                                            + values[1] + values[5] * (1 - values[1].a)
                                            + values[2] + values[6] * (1 - values[2].a)
                                            + values[3] + values[7] * (1 - values[3].a)) / 4);


//	imageStore(target, storePos, sourceValue);
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
