uniform int indirect = 1;
uniform int entityIndex = 0;

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
layout(std430, binding=7) buffer _vertices {
    VertexPacked vertices[];
};
out vec3 WorldPos_CS_in;
out vec2 TexCoord_CS_in;
out vec3 Normal_CS_in;
out int entityBufferIndex_CS_in;

void main()
{
    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    Entity entity = entities[entityBufferIndex];
    entityBufferIndex_CS_in = entityBufferIndex;
    Material material = materials[entity.materialIndex];

    VertexPacked vertex = vertices[gl_VertexID];

    WorldPos_CS_in = (entity.modelMatrix * vec4(vertex.position.xyz, 1.0)).xyz;
    if(material.useWorldSpaceXZAsTexCoords == 1) {
        vertex.texCoord.xy = WorldPos_CS_in.xz;
    }

    if(entity.invertTexcoordY == 1) {
        vertex.texCoord.y = 1 - vertex.texCoord.y;
    }
    TexCoord_CS_in = vertex.texCoord.xy * material.uvScale;
    Normal_CS_in = (entity.modelMatrix * vec4(vertex.normal.xyz, 0.0)).xyz;
}