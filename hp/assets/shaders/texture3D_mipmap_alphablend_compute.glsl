#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) readonly uniform image3D source;
layout(binding=1, rgba8) writeonly uniform image3D target;
layout(binding=2, rgba8) writeonly uniform image3D targetTwo;
layout(binding=3, rgba8) readonly uniform image3D normalSource;

//include(globals.glsl)

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

vec4 calculateMipMap(ivec3 pos) {

    const int kernelSizeHalf = 2;
    const int kernelSize = 2 * kernelSizeHalf +1;

    vec3 centerNormal = Decode(imageLoad(normalSource, pos).xy);

    vec3 result = vec3(0);
    float alpha = 0;
    for(int x = -kernelSizeHalf; x < kernelSizeHalf; x++) {
      for(int y = -kernelSizeHalf; y < kernelSizeHalf; y++) {
        for(int z = -kernelSizeHalf; z < kernelSizeHalf; z++) {
          vec4 currentValue = imageLoad(source, pos + ivec3(x, y, z));
          vec3 currentNormal = Decode(imageLoad(normalSource, pos + ivec3(x, y, z)).xy);
          float clampDot = 1;
//          float clampDot = clamp(dot(centerNormal, currentNormal), 0, 1);
//          if(clampDot > 0.0) { clampDot = 1; }
          result += currentValue.a * currentValue.rgb * clampDot;
          alpha += currentValue.a;
        }
      }
    }

    if(alpha < 0.0000001) { return vec4(0);}
    float denominator = (kernelSize*kernelSize*kernelSize);
    return vec4(result/alpha, alpha/denominator);
}
vec4 calculateMipMapNormalWeighted(ivec3 pos) {

    const int kernelSizeHalf = 2;
    const int kernelSize = 2 * kernelSizeHalf +1;

    vec4 centerSample = imageLoad(source, pos);
    vec4 centerNormalSample = imageLoad(normalSource, pos);
    vec3 result = centerSample.rgb * centerSample.a;
    float alpha = centerSample.a;

    for(int x = -kernelSizeHalf; x < kernelSizeHalf; x++) {
      for(int y = -kernelSizeHalf; y < kernelSizeHalf; y++) {
        for(int z = -kernelSizeHalf; z < kernelSizeHalf; z++) {
          vec4 currentValue = imageLoad(source, pos + ivec3(x, y, z));
          vec4 currentNormalValue = imageLoad(normalSource, pos + ivec3(x, y, z));
          float clampDot = clamp(dot(centerNormalSample.xyz, currentNormalValue.xyz), 0.0, 1.0);
//          currentValue.rgb *= clampDot > 0.0 ? 1.0 : 0.0;
          result += currentValue.a * currentValue.rgb;
          alpha += currentValue.a;
        }
      }
    }

    if(alpha < 0.0000001) { return vec4(0);}
    float denominator = (kernelSize*kernelSize*kernelSize);
    return vec4(result/alpha, alpha/denominator);
}

float dotSaturate(vec3 a, vec3 b) {
    return 1.;
//    return clamp(dot(a,b), 0.0f, 1.0f);
}

void main(void) {
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);
	ivec3 loadPosition = (storePos+ivec3(1))*2-ivec3(1); // TODO: Fix this shit

//	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
//	float weightSum = 0;
//
//	vec4 values[8] = fetchTexels(loadPosition);
//	vec3 normalValues[8] = fetchNormalTexels(loadPosition);
//	vec4 finalValue = (values[0] + values[4] * (1 - values[0].a) * dotSaturate(normalValues[0], -(normalValues[4]))
//                     + values[1] + values[5] * (1 - values[1].a) * dotSaturate(normalValues[5], -(normalValues[1]))
//                     + values[2] + values[6] * (1 - values[2].a) * dotSaturate(normalValues[6], -(normalValues[2]))
//                     + values[3] + values[7] * (1 - values[3].a) * dotSaturate(normalValues[7], -(normalValues[3]))) / 4;
//    imageStore(target, storePos, finalValue);
    imageStore(target, storePos, calculateMipMap(loadPosition));
//    imageStore(target, storePos, calculateMipMapNormalWeighted(loadPosition));

//	imageStore(target, storePos, sourceValue);
//	imageStore(target, ivec3(0,0,0), vec4(1,0,0,1));
}
