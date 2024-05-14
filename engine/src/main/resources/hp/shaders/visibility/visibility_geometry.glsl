
layout ( triangles ) in;
layout ( triangle_strip, max_vertices = 3 ) out;

//include(globals_structs.glsl)

flat in VertexShaderFlatOutput vertexShaderFlatOutput[3];
in VertexShaderOutput vertexShaderOutput[3];

flat out VertexShaderFlatOutput geometryShaderFlatOutput;
out VertexShaderOutput geometryShaderOutput;
out vec3 geometryShaderBarycentrics;
flat out ivec3 geometryShaderVertexIds;

void main()
{
    for(int i = 0; i < 3; i++) {
        geometryShaderFlatOutput = vertexShaderFlatOutput[i];
        geometryShaderOutput = vertexShaderOutput[i];
        if(i == 0) {
            geometryShaderBarycentrics = vec3(1,0,0);
        } else if(i == 1) {
            geometryShaderBarycentrics = vec3(0,1,0);
        } else if(i == 2) {
            geometryShaderBarycentrics = vec3(0,0,1);
        }
        geometryShaderVertexIds.x = vertexShaderFlatOutput[0].vertexIndex;
        geometryShaderVertexIds.y = vertexShaderFlatOutput[1].vertexIndex;
        geometryShaderVertexIds.z = vertexShaderFlatOutput[2].vertexIndex;

        gl_Position = gl_in[i].gl_Position;
        gl_PrimitiveID = gl_PrimitiveIDIn;
        EmitVertex();
    }
    EndPrimitive();
}