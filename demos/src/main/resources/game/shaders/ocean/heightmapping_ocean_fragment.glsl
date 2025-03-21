
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

uniform int time = 0;

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
layout(location=4)out vec4 out_indices;

//include(normals.glsl)

vec3 calculateFaceNormal(vec3 a, vec3 b, vec3 c) {
    vec3 dir = cross(b-a, c-a);
    return normalize(dir);
}

float random (in vec2 _st) {
    return fract(sin(dot(_st.xy,
    vec2(12.9898,78.233)))*
    43758.5453123);
}

// Based on Morgan McGuire @morgan3d
// https://www.shadertoy.com/view/4dS3Wd
float noise (in vec2 _st) {
    vec2 i = floor(_st);
    vec2 f = fract(_st);

    // Four corners in 2D of a tile
    float a = random(i);
    float b = random(i + vec2(1.0, 0.0));
    float c = random(i + vec2(0.0, 1.0));
    float d = random(i + vec2(1.0, 1.0));

    vec2 u = f * f * (3.0 - 2.0 * f);

    return mix(a, b, u.x) +
    (c - a)* u.y * (1.0 - u.x) +
    (d - b) * u.x * u.y;
}

#define NUM_OCTAVES 5

float fbm ( in vec2 _st) {
    float v = 0.0;
    float a = 0.5;
    vec2 shift = vec2(100.0);
    // Rotate to reduce axial bias
    mat2 rot = mat2(cos(0.5), sin(0.5),
    -sin(0.5), cos(0.50));
    for (int i = 0; i < NUM_OCTAVES; ++i) {
        v += a * noise(_st);
        _st = rot * _st * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

const mat2 myt = mat2(.12121212, .13131313, -.13131313, .12121212);
const vec2 mys = vec2(1e4, 1e6);

vec2 rhash(vec2 uv) {
    uv *= myt;
    uv *= mys;
    return fract(fract(uv / mys) * uv);
}

vec3 hash(vec3 p) {
    return fract(sin(vec3(dot(p, vec3(1.0, 57.0, 113.0)),
    dot(p, vec3(57.0, 113.0, 1.0)),
    dot(p, vec3(113.0, 1.0, 57.0)))) *
    43758.5453);
}

float voronoi2d(const in vec2 point) {
    vec2 p = floor(point);
    vec2 f = fract(point);
    float res = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 b = vec2(i, j);
            vec2 r = vec2(b) - f + rhash(p + b);
            res += 1. / pow(dot(r, r), 8.);
        }
    }
    return pow(1. / res, 0.0625);
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
        vec2 normalUV = 4.0f*(UV + 0.00001f * vec2(time%100000));
        vec3 PN_world = normalize(perturb_normal(Normal_FS_in, V, normalUV, normalMap));
        vec3 PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);

        vec2 normalUV2 = (UV + 0.00005f * vec2(time%100000));
        vec3 PN_world2 = normalize(perturb_normal(Normal_FS_in, V, normalUV2, normalMap));
        vec3 PN_view2 = normalize((viewMatrix * vec4(PN_world2, 0)).xyz);

        out_normalAmbient.xyz = max(PN_view, PN_view2);

        const bool useNiceNoise = false;
        if(useNiceNoise) {
            /////////// https://thebookofshaders.com/13/
            vec3 colorX = vec3(0.0);

            vec2 st = normalUV;
            vec2 q = vec2(0.);
            float deltaSeconds = (time%10000)*0.0001f;
            q.x = fbm( st + 0.00*deltaSeconds);
            q.y = fbm( st + vec2(1.0));

            vec2 r = vec2(0.);
            r.x = fbm( st + 1.0*q + vec2(1.7,9.2)+ 0.15*deltaSeconds );
            r.y = fbm( st + 1.0*q + vec2(8.3,2.8)+ 0.126*deltaSeconds);

            float f = fbm(st+r);
            colorX = mix(vec3(0.101961,0.619608,0.666667), vec3(0.666667,0.666667,0.498039), clamp((f*f)*4.0,0.0,1.0));
            colorX = mix(colorX, vec3(0,0,0.164706), clamp(length(q),0.0,1.0));
            colorX = mix(colorX, vec3(0.666667,1,1), clamp(length(r.x),0.0,1.0));

            out_normalAmbient.xyz = mix(PN_view, PN_view2, colorX);
            color.rgb += color.rgb * colorX;
            ///////////
        }
        vec3 displacement = clamp(textureLod(displacementMap, UV, 0).xyz, 0.0f, 10.0f);

        const bool useVoronoiFoam = true;
        if(useVoronoiFoam) {
            //////////////// voronoi foam, based on https://github.com/MaxBittker/glsl-voronoi-noise/blob/master/2d.glsl
            vec3 voronoi = 0.5f * vec3(voronoi2d(1.0f * displacement.y * normalUV))  * PN_world.y;
            voronoi += 0.25f * vec3(voronoi2d(220.0f * clamp(displacement.y/20.0f, 0, 5) * normalUV));
            voronoi += 0.25f * vec3(voronoi2d(160.0f * normalUV));
            color.rgb += voronoi * displacement.y;
            ////////////////
        } else {
            color.rgb += 3 * color.rgb * displacement;
        }
    }
    if(hasRoughnessMap) {
        float r = textureLod(roughnessMap, UV, 0).x;
        out_positionRoughness.w = material.roughness*r;
    }

    out_colorMetallic = vec4(color.rgb, material.metallic);
    out_motionDepthTransparency = vec4(motionVec,depth,material.transparency);
    out_indices = vec4(float(entity.entityIndexWithoutMeshIndex), entityBufferIndex_FS_in, entity.materialIndex, float(entity.meshIndex));
}