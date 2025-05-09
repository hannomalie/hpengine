
layout (vertices = 3) out;
uniform vec3 eyePosition;

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
    Material materials[100];
};
layout(std430, binding=3) buffer _entities {
    Entity entities[2000];
};

// attributes of the input CPs
in vec3 WorldPos_CS_in[];
in vec2 TexCoord_CS_in[];
in vec3 Normal_CS_in[];
in int entityBufferIndex_CS_in[];

// attributes of the output CPs
out vec3 WorldPos_ES_in[];
out vec2 TexCoord_ES_in[];
out vec3 Normal_ES_in[];
out int entityBufferIndex_ES_in[];

float GetTessLevel(float Distance0, float Distance1)
{
    float AvgDistance = (Distance0 + Distance1) / 2.0;

    if (AvgDistance <= 2.0) {
        return 10.0;
    }
    else if (AvgDistance <= 500.0) {
        return 7.0;
    }
    else {
        return 3.0;
    }
}

float getTesselationLevel(float Distance0, float Distance1, Material material) {
//    return gl_MaxTessGenLevel;

    float d = (Distance0 + Distance1) / 2.0f;

    if(d < 10) return gl_MaxTessGenLevel;
    float lodFactor = d / 100.0f;//material.lodFactor;
    lodFactor = 1-clamp(lodFactor, 0.0f, 1.0f);
    return mix(3, gl_MaxTessGenLevel, pow(lodFactor, 2));

    if(d < 10) return gl_MaxTessGenLevel;
    if(d < 50) return 32;
    if(d < 100) return 16;
    if(d < 200) return 8;
    if(d < 400) return 4;
    return 3;
}

void main()
{
    Material material_CS_in = materials[entities[entityBufferIndex_ES_in[0]].materialIndex];
    // Set the control points of the output patch
    TexCoord_ES_in[gl_InvocationID] = TexCoord_CS_in[gl_InvocationID];
    Normal_ES_in[gl_InvocationID] = Normal_CS_in[gl_InvocationID];
    WorldPos_ES_in[gl_InvocationID] = WorldPos_CS_in[gl_InvocationID];
    entityBufferIndex_ES_in[gl_InvocationID] = entityBufferIndex_CS_in[gl_InvocationID];

    // Calculate the distance from the camera to the three control points
    float EyeToVertexDistance0 = distance(eyePosition, WorldPos_ES_in[0]);
    float EyeToVertexDistance1 = distance(eyePosition, WorldPos_ES_in[1]);
    float EyeToVertexDistance2 = distance(eyePosition, WorldPos_ES_in[2]);

    // Calculate the tessellation levels
    gl_TessLevelOuter[0] = getTesselationLevel(EyeToVertexDistance1, EyeToVertexDistance2, material_CS_in);
    gl_TessLevelOuter[1] = getTesselationLevel(EyeToVertexDistance2, EyeToVertexDistance0, material_CS_in);
    gl_TessLevelOuter[2] = getTesselationLevel(EyeToVertexDistance0, EyeToVertexDistance1, material_CS_in);
    gl_TessLevelInner[0] = gl_TessLevelOuter[2];
}