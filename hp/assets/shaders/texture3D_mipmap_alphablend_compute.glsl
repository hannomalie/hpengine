#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) readonly uniform image3D source;
layout(binding=1, rgba8) writeonly uniform image3D target;
layout(binding=2, rgba8) writeonly uniform image3D targetTwo;
layout(binding=3, rgba8) readonly uniform image3D normalSource;

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
vec3[8] fetchNormalTexels(ivec3 pos) {
  return vec3[8] (imageLoad(normalSource, pos + ivec3(1, 1, 1)).rgb,
                  imageLoad(normalSource, pos + ivec3(1, 1, 0)).rgb,
                  imageLoad(normalSource, pos + ivec3(1, 0, 1)).rgb,
                  imageLoad(normalSource, pos + ivec3(1, 0, 0)).rgb,
                  imageLoad(normalSource, pos + ivec3(0, 1, 1)).rgb,
                  imageLoad(normalSource, pos + ivec3(0, 1, 0)).rgb,
                  imageLoad(normalSource, pos + ivec3(0, 0, 1)).rgb,
                  imageLoad(normalSource, pos + ivec3(0, 0, 0)).rgb);
}

float dotSaturate(vec3 a, vec3 b) {
    return 1;
//    return clamp(dot(a,b), 0.25f, 1);
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
	vec3 normalValues[8] = fetchNormalTexels(loadPosition);
	vec4 finalValue = (values[0] + values[4] * (1 - values[0].a) * dotSaturate(normalValues[0], -(normalValues[4]))
                                                             + values[1] + values[5] * (1 - values[1].a) * dotSaturate(normalValues[5], -(normalValues[1]))
                                                             + values[2] + values[6] * (1 - values[2].a) * dotSaturate(normalValues[6], -(normalValues[2]))
                                                             + values[3] + values[7] * (1 - values[3].a) * dotSaturate(normalValues[7], -(normalValues[3]))) / 4;
    imageStore(target, storePos, finalValue);
//    imageStore(targetTwo, storePos, finalValue);

//	imageStore(target, storePos, sourceValue);
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
