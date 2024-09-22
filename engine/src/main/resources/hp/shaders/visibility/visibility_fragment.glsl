//include(globals_structs.glsl)
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

layout(binding=2) uniform sampler2DArray diffuseTextures;

layout(location=0)out vec4 out_visibility;
layout(location=1)out vec4 out_normal;

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
    vec3 normal_world = vertexShaderOutput.normal_world;
    vec2 uv = material.uvScale * vertexShaderOutput.texCoord;
    float alpha = 1.0f;
    float mipLevel = 0;

    sampler2D diffuseMap;
    bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
    if(hasDiffuseMap) {
		diffuseMap = sampler2D(material.handleDiffuse);
        mipLevel = textureQueryLod(diffuseMap, uv).r;
        alpha = textureLod(diffuseMap, uv, 0).a;
    }
    sampler2D normalMap;
    bool hasNormalMap = uint64_t(material.handleNormal) > 0;
    if(hasNormalMap) { normalMap = sampler2D(material.handleNormal); }
    if(hasNormalMap) {
        vec3 positionWorld = vertexShaderOutput.position_world.xyz;
        vec3 positionView = (viewMatrix * vec4(positionWorld, 1)).xyz;
        vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
        position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
        vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
        dir.w = 0.0;
        vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
        normal_world = normalize(perturb_normal(normal_world, V, uv, normalMap));
    }

    if(alpha < 0.98f) {
        discard;
    } else {
        out_visibility = vec4(uv, mipLevel, entityIndex);
        out_normal = vec4(normal_world, 0);
    }
}
