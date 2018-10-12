#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) writeonly uniform image3D albedoGrid;
layout(binding=1, rgba8) uniform image3D normalGrid;
layout(binding=2, rgba8) writeonly uniform image3D grid1;
layout(binding=3, rgba8) writeonly uniform image3D grid2;

//include(globals_structs.glsl)

layout(std430, binding=5) buffer _voxelGrids {
    VoxelGridArray voxelGridArray;
};
uniform int voxelGridIndex = 0;

void main(void) {
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);

    VoxelGrid grid = voxelGridArray.voxelGrids[voxelGridIndex];
    float sceneScale = grid.scale;
    float inverseSceneScale = 1f/sceneScale;

    sampler3D normalGridTexture = sampler3D(uint64_t(grid.normalGridHandle));

	vec4 currentValue = texelFetch(normalGridTexture, storePos, 0);
	float isStatic = currentValue.b;
	if((isStatic < 0.9))
	{
        imageStore(albedoGrid, storePos, vec4(0,0,0,0));
        imageStore(normalGrid, storePos, vec4(0,0,0,0));
        imageStore(grid2, storePos, vec4(0,0,0,0));
	}
}
