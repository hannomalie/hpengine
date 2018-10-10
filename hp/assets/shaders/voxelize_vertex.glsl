#extension GL_ARB_shader_draw_parameters : require

uniform int entityIndex;
uniform int entityBaseIndex;

uniform int indirect = 1;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=4) buffer _entityOffsets {
	int entityOffsets[2000];
};
layout(std430, binding=5) buffer _voxelGrids {
    int size;
    int dummy0;
    int dummy1;
    int dummy2;
	VoxelGrid voxelGrids[10];
};

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Tangent;
in vec3 in_Binormal;

out vec3 normal_world;
out vec4 position_world;

out vec3 v_vertex;
out vec3 v_normal;
out vec2 v_texcoord;
out int v_materialIndex;
out int v_isStatic;


uniform vec3 lightDirection;
uniform vec3 lightColor;

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    VoxelGrid grid = voxelGrids[0];
    int gridSize = grid.resolution;

    Entity entity = entities[entityBufferIndex];
    v_isStatic = int(entity.isStatic);

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];
    v_materialIndex = materialIndex;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	vec3 normal_model = in_Normal;

	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;

	v_vertex = position_world.xyz;
	v_normal = normal_world.xyz;
	v_texcoord = in_TextureCoord;

//    gl_Position = position_world;
}