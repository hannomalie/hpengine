
#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D displacementMap;
uniform bool hasDisplacementMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
    Material materials[100];
};
layout(std430, binding=3) buffer _entities {
    Entity entities[2000];
};

uniform vec3 eyePosition;
uniform mat4 viewProjectionMatrix;
uniform mat4 viewMatrix;

in vec3 WorldPos_FS_in;
in vec2 TexCoord_FS_in;
in vec3 Normal_FS_in;
flat in int entityBufferIndex_FS_in;
flat in Material material_FS_in;

layout(location=0)out vec4 out_positionRoughness;
layout(location=1)out vec4 out_normalAmbient;
layout(location=2)out vec4 out_colorMetallic;
layout(location=3)out vec4 out_motionDepthTransparency;
layout(location=4)out vec4 out_depthAndIndices;

//include(normals.glsl)

vec3 calculateFaceNormal(vec3 a, vec3 b, vec3 c) {
    vec3 dir = cross(b-a, c-a);
    return normalize(dir);
}

void main()
{
    Entity entity = entities[entityBufferIndex_FS_in];
    vec2 motionVec = vec2(0); // TODO implement me
    float depth = gl_FragDepth; // TODO implement me
    Material material = materials[entity.materialIndex];//material_FS_in;
    vec2 UV = TexCoord_FS_in.xy;
    vec3 V = -normalize((WorldPos_FS_in.xyz + eyePosition.xyz).xyz);
    out_positionRoughness = vec4((viewMatrix * vec4(WorldPos_FS_in, 1.0f)).xyz, material.roughness);
    out_normalAmbient = vec4((viewMatrix * vec4(Normal_FS_in, 0.0f)).xyz, material.ambient);

    vec4 color = vec4(material.diffuse, 1);
    float alpha = material.transparency;

#ifdef BINDLESSTEXTURES
    sampler2D diffuseMap;
    bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
    if(hasDiffuseMap) { diffuseMap = sampler2D(material.handleDiffuse); }

    sampler2D normalMap;
    bool hasNormalMap = uint64_t(material.handleNormal) > 0;
    if(hasNormalMap) { normalMap = sampler2D(material.handleNormal); }

    sampler2D specularMap;
    bool hasSpecularMap = uint64_t(material.handleSpecular) > 0;
    if(hasSpecularMap) { specularMap = sampler2D(material.handleSpecular); }

    sampler2D heightMap;
    bool hasHeightMap = uint64_t(material.handleHeight) > 0;
    if(hasHeightMap) { heightMap = sampler2D(material.handleHeight); };

    sampler2D displacementMap;
    bool hasDisplacementMap = uint64_t(material.handleDisplacement) > 0;
    if(hasDisplacementMap) { displacementMap = sampler2D(material.handleDisplacement); }

    sampler2D roughnessMap;
    bool hasRoughnessMap = uint64_t(material.handleRoughness) >= 0;
    if(hasRoughnessMap) { roughnessMap = sampler2D(material.handleRoughness); }
#endif

    if(hasDiffuseMap) {
        color = textureLod(diffuseMap, UV, 0);
        alpha *= color.a;
    }
    if(hasNormalMap) {
        vec3 PN_world = normalize(perturb_normal(Normal_FS_in, V, UV, normalMap));
        vec3 PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
        //                        out_normalAmbient.xyz = PN_view.xyz;
        out_normalAmbient.xyz = normalize((viewMatrix * vec4(textureLod(normalMap, UV, 0).xyz, 0)).xyz);
    }
    if(hasRoughnessMap) {
        float r = textureLod(roughnessMap, UV, 0).x;
        out_positionRoughness.w = material.roughness*r;
    }

//    out_colorMetallic = vec4(UV, 1, material.metallic);
    out_colorMetallic = vec4(color.rgb, material.metallic);
    out_motionDepthTransparency = vec4(motionVec,depth,material.transparency);
    out_depthAndIndices = vec4(float(entity.entityIndexWithoutMeshIndex), depth, entity.materialIndex, float(entity.meshIndex));
}