//include(globals_structs.glsl)

flat in VertexShaderFlatOutput geometryShaderFlatOutput;
in VertexShaderOutput geometryShaderOutput;
in vec3 geometryShaderBarycentrics;
flat in ivec3 geometryShaderVertexIds;

layout(location=0)out vec4 out_triangleIndex;
//layout(location=0)out ivec4 out_triangleIndex;
layout(location=1)out vec4 out_barycentrics;

//include(globals.glsl)
//include(normals.glsl)

void main(void) {

    int entityIndex = geometryShaderFlatOutput.entityBufferIndex;

    out_triangleIndex.r = gl_PrimitiveID;
    out_triangleIndex.gba = geometryShaderVertexIds;
    out_barycentrics = vec4(geometryShaderBarycentrics.xyz, entityIndex);
}
