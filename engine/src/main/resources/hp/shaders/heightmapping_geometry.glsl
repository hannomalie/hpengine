
layout ( triangles ) in;
layout ( triangle_strip, max_vertices = 3 ) out;

uniform mat4 viewProjectionMatrix;
uniform bool hasDisplacementMap = false;

//include(globals_structs.glsl)

in vec3 WorldPos_GS_in[3];
in vec2 TexCoord_GS_in[3];
in vec3 Normal_GS_in[3];
flat in Material material_GS_in[3];
flat in int entityBufferIndex_GS_in[3];

out vec3 WorldPos_FS_in;
out vec2 TexCoord_FS_in;
out vec3 Normal_FS_in;
flat out Material material_FS_in;
flat out int entityBufferIndex_FS_in;

vec3 calculateFaceNormal(vec3 a, vec3 b, vec3 c) {
    vec3 dir = cross(b-a, c-a);
    return normalize(dir);
}

void main()
{

    for(int i = 0; i < 3; i++) {
        WorldPos_FS_in = WorldPos_GS_in[i];
        TexCoord_FS_in = TexCoord_GS_in[i];
        Normal_FS_in = calculateFaceNormal(WorldPos_GS_in[0], WorldPos_GS_in[1], WorldPos_GS_in[2]);//Normal_GS_in[i];
        material_FS_in = material_GS_in[i];
        entityBufferIndex_FS_in = entityBufferIndex_GS_in[i];
        gl_Position = viewProjectionMatrix * vec4(WorldPos_GS_in[i], 1.0f);
        EmitVertex();
    }
    EndPrimitive();
}