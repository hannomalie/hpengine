#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) writeonly uniform image3D outputVoxelGrid;

#ifdef BINDLESSTEXTURES
#else
layout(binding=1) uniform sampler3D albedoGrid;
layout(binding=2) uniform sampler3D normalGrid;
layout(binding=3) uniform sampler3D grid;
#endif
uniform int bounces = 1;
uniform int lightInjectedFramesAgo = 1;

//include(globals_structs.glsl)
//include(globals.glsl)

layout(std430, binding=5) buffer _voxelGrids {
    VoxelGridArray voxelGridArray;
};
uniform int voxelGridIndex = 0;

vec4 voxelTraceConeXXX(VoxelGrid voxelGrid, sampler3D grid, vec3 origin, vec3 dir, float coneRatio, float maxDist) {

    vec4 accum = vec4(0.0);
    float alpha = 0;
    float dist = 0;
    vec3 samplePos = origin;// + dir;

    while (dist <= maxDist && alpha < 1.0)
    {
        float minScale = 100000.0;
        float minVoxelDiameter = 0.5*voxelGrid.scale;
        float minVoxelDiameterInv = 1.0/minVoxelDiameter;
        vec4 ambientLightColor = vec4(0.);
        float diameter = max(minVoxelDiameter, 2 * coneRatio * (1+dist));
        float increment = diameter;

        int gridSize = voxelGrid.resolution;

        float sampleLOD = log2(diameter * minVoxelDiameterInv);
        vec4 sampleValue = voxelFetch(voxelGrid, grid, samplePos, sampleLOD);

        accum.rgb += sampleValue.rgb;
        float a = 1 - alpha;
        alpha += a * sampleValue.a;

        dist += increment;
        samplePos = origin + dir * dist;
        increment *= 1.25f;
    }
	return vec4(accum.rgb, alpha);
}

#ifdef BINDLESSTEXTURES
vec4 traceVoxelsDiffuse2(VoxelGridArray voxelGridArray, vec3 normalWorld, vec3 positionWorld) {
    vec4 voxelDiffuse;
    for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
        VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];
        sampler3D grid = toSampler(voxelGrid.gridHandle);
        int gridSize = voxelGrid.resolution;
        float sceneScale = voxelGrid.scale;

        float minVoxelDiameter = sceneScale;
        float maxDist = 50;
        const int SAMPLE_COUNT = 7;

        for (int k = 0; k < SAMPLE_COUNT; k++) {
            const float PI = 3.1415926536;
            vec2 Xi = hammersley2d(k, SAMPLE_COUNT);
            float Phi = 2 * PI * Xi.x;
            float a = 0.5;
            float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )) );
            float SinTheta = sqrt( 1 - CosTheta * CosTheta );

            vec3 H;
            H.x = SinTheta * cos( Phi );
            H.y = SinTheta * sin( Phi );
            H.z = CosTheta;
            H = hemisphereSample_uniform(Xi.x, Xi.y, normalWorld);

            float coneRatio = 0.0125 * sceneScale;
            float dotProd = clamp(dot(normalWorld, H),0,1);
            voxelDiffuse += vec4(dotProd) * voxelTraceConeXXX(voxelGrid, grid, positionWorld, normalize(H), coneRatio, maxDist);
        }
    }

    return voxelDiffuse;
}
#else
vec4 traceVoxelsDiffuse2(VoxelGridArray voxelGridArray, vec3 normalWorld, vec3 positionWorld) {
    return vec4(1,0,0,0);
}
#endif

void main(void) {
    VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);

    #ifdef BINDLESSTEXTURES
    sampler3D albedoGrid = toSampler(voxelGrid.albedoGridHandle);
    sampler3D normalGrid = toSampler(voxelGrid.normalGridHandle);
    sampler3D grid = toSampler(voxelGrid.gridHandle);
    #endif

	vec3 positionWorld = gridToWorldPosition(voxelGrid, storePos);

    vec4 color = voxelFetch(voxelGrid, albedoGrid, positionWorld, 0);
    float opacity = color.a;
    if(opacity > 0.0001f) {
        vec4 normalStatic = voxelFetch(voxelGrid, normalGrid, positionWorld, 0);
        vec3 normalWorld = normalize(normalStatic.rgb);
        float emissive = normalStatic.a;

        vec4 firstBounce = voxelFetch(voxelGrid, grid, positionWorld, 0);

        vec4 diffuseVoxelTraced = traceVoxelsDiffuse2(voxelGridArray, normalize(normalWorld), positionWorld);
//TODO: MAKE THIS THING WORK SOMEHOW

        vec3 maxMultipleBounce = vec3(0.005f);
        vec3 multipleBounceColor = maxMultipleBounce*diffuseVoxelTraced.rgb;

        vec3 finalVoxelColor = color.rgb*multipleBounceColor.rgb;
//        finalVoxelColor.rgb = color.rgb*0.1f;
//        finalVoxelColor = vec3(0);

//        imageStore(outputVoxelGrid, storePos, vec4(firstBounce.rgb + finalVoxelColor, opacity));
    }
}
