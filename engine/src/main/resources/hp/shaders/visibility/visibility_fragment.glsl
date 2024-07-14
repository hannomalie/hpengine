//include(globals_structs.glsl)

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

layout(binding=2) uniform sampler2DArray diffuseTextures;

layout(location=0)out vec4 out_visibility;

//include(globals.glsl)
//include(normals.glsl)

layout(std430, binding=1) buffer _materials {
    Material materials[100];
};
layout(std430, binding=3) buffer _entities {
    Entity entities[2000];
};
// https://community.khronos.org/t/mipmap-level-calculation-using-dfdx-dfdy/67480
float mip_map_level(in vec2 texture_coordinate)
{
    // The OpenGL Graphics System: A Specification 4.2
    //  - chapter 3.9.11, equation 3.21
    vec2  dx_vtc        = dFdx(texture_coordinate);
    vec2  dy_vtc        = dFdy(texture_coordinate);
    float delta_max_sqr = max(dot(dx_vtc, dx_vtc), dot(dy_vtc, dy_vtc));

    return 0.5 * log2(delta_max_sqr);
}
void main(void) {

    int entityIndex = vertexShaderFlatOutput.entityBufferIndex;

    Entity entity = entities[entityIndex];
    Material material = materials[entity.materialIndex];
    vec2 uv = material.uvScale * vertexShaderOutput.texCoord;
    float alpha = 1.0f;
    float mipLevel = 0;

#ifdef BINDLESSTEXTURES
    sampler2D diffuseMap;
    bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
    if(hasDiffuseMap) {
		diffuseMap = sampler2D(material.handleDiffuse);
        mipLevel = textureQueryLod(diffuseMap, uv).r;
        alpha = textureLod(diffuseMap, uv, 0).a;
    }
#else
    mipLevel = textureQueryLod(diffuseTextures, vec3(uv, material.diffuseMapIndex)).r;
    alpha = textureLod(diffuseTextures, vec3(uv, material.diffuseMapIndex), 0).a;
#endif

    if(alpha < 0.98f) {
        discard;
    } else {
        out_visibility = vec4(uv, mipLevel, entityIndex);
    }
}
