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
    VoxelGrid[MAX_VOXELGRIDS] voxelGrids;
};
layout(std430, binding=7) buffer _vertices {
    VertexPacked vertices[];
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
out int v_entityIndex;
out int v_isStatic;


uniform vec3 lightDirection;
uniform vec3 lightColor;
uniform int voxelGridIndex = 0;

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    VertexPacked vertex = vertices[gl_VertexID];
    vertex.position.w = 1;

    VoxelGrid grid = voxelGrids[voxelGridIndex];
    int gridSize = grid.resolution;

    Entity entity = entities[entityBufferIndex];
    v_isStatic = int(entity.isStatic);

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];
    v_materialIndex = materialIndex;
    v_entityIndex = entityBufferIndex;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(vertex.position.xyz,1);
	position_world = modelMatrix * positionModel;

	vec3 normal_model = in_Normal;

	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;

	v_vertex = position_world.xyz;
	v_normal = normal_world.xyz;
	v_texcoord = vertex.texCoord.xy;

    if(entity.invertTexcoordY == 1) {
        v_texcoord.y = 1 - v_texcoord.y;
    }

//    gl_Position = position_world;
}