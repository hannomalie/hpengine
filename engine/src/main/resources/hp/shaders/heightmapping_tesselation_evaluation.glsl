
//                layout(triangles, equal_spacing, ccw) in;
layout(triangles, fractional_even_spacing, ccw) in;

#ifdef BINDLESSTEXTURES
#else
layout(binding=3) uniform sampler2D displacementMap;
uniform bool hasDisplacementMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
#endif

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
    Material materials[100];
};
layout(std430, binding=3) buffer _entities {
    Entity entities[2000];
};

uniform mat4 viewProjectionMatrix;
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=4) uniform sampler2D gDisplacementMap;
uniform float gDispFactor = 1.0f;

in vec3 WorldPos_ES_in[];
in vec2 TexCoord_ES_in[];
in vec3 Normal_ES_in[];
in int entityBufferIndex_ES_in[];

out vec3 WorldPos_GS_in;
out vec2 TexCoord_GS_in;
out vec3 Normal_GS_in;
flat out Material material_GS_in;
flat out int entityBufferIndex_GS_in;

vec2 interpolate2D(vec2 v0, vec2 v1, vec2 v2)
{
    return vec2(gl_TessCoord.x) * v0 + vec2(gl_TessCoord.y) * v1 + vec2(gl_TessCoord.z) * v2;
}

vec3 interpolate3D(vec3 v0, vec3 v1, vec3 v2)
{
    return vec3(gl_TessCoord.x) * v0 + vec3(gl_TessCoord.y) * v1 + vec3(gl_TessCoord.z) * v2;
}

void main()
{
    Material material = materials[entities[entityBufferIndex_ES_in[0]].materialIndex];


#ifdef BINDLESSTEXTURES
    sampler2D heightMap;
    sampler2D displacementMap;
    bool hasDisplacementMap = uint64_t(material.handleDisplacement) > 0;
    if(hasDisplacementMap) { displacementMap = sampler2D(material.handleDisplacement); };

    bool hasHeightMap = uint64_t(material.handleHeight) > 0;
    if(hasHeightMap) { heightMap = sampler2D(material.handleHeight); };
#endif


    // Interpolate the attributes of the output vertex using the barycentric coordinates
    TexCoord_GS_in = interpolate2D(TexCoord_ES_in[0], TexCoord_ES_in[1], TexCoord_ES_in[2]);
    WorldPos_GS_in = interpolate3D(WorldPos_ES_in[0], WorldPos_ES_in[1], WorldPos_ES_in[2]);
    material_GS_in = material;
    entityBufferIndex_GS_in = entityBufferIndex_ES_in[0];
    Normal_GS_in = interpolate3D(Normal_ES_in[0], Normal_ES_in[1], Normal_ES_in[2]);
    Normal_GS_in = normalize(Normal_GS_in);
    // Displace the vertex along the normal
    if(hasDisplacementMap) {
        vec4 displacementMapSample = texture(displacementMap, TexCoord_GS_in.xy);
        WorldPos_GS_in += Normal_GS_in * displacementMapSample.xyz * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;
        //xxxWorldPos_GS_in += Normal_GS_in * displacementMapSample.y * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;

    } else if(hasHeightMap) {
        WorldPos_GS_in += Normal_GS_in * texture(heightMap, TexCoord_GS_in.xy).x * gDispFactor * material.parallaxScale - Normal_GS_in * gDispFactor * material.parallaxBias;
//        WorldPos_GS_in.y += 10f;
    }
    gl_Position = viewProjectionMatrix * vec4(WorldPos_GS_in, 1.0f);
}