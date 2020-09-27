
#if defined(BINDLESSTEXTURES) && defined(SHADER5)
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D occlusionMap;
uniform bool hasOcclusionMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif

layout(binding=3, rgba8) uniform image3D out_voxelNormal;
layout(binding=5, rgba8) uniform image3D out_voxelAlbedo;
layout(binding=6, r16i) uniform iimage3D out_index;

flat in int g_axis;   //indicate which axis the projection uses
flat in vec4 g_AABB;

in vec3 g_normal;
in vec3 g_pos;
in vec3 g_posWorld;
in vec2 g_texcoord;
flat in int g_materialIndex;
flat in int g_entityIndex;
flat in int g_isStatic;
//layout (pixel_center_integer) in vec4 gl_FragCoord;


//include(globals_structs.glsl)
//include(globals.glsl)


uniform int entityIndex;

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=5) buffer _voxelGrids {
    VoxelGrid[MAX_VOXELGRIDS] voxelGrids;
};

uniform vec3 lightDirection;
uniform vec3 lightColor;

uniform int voxelGridIndex = 0;
uniform int voxelGridCount = 0;


#if defined(BINDLESSTEXTURES) && defined(SHADER5)
vec4 traceVoxelsDiffuseBla(VoxelGridArray voxelGridArray, vec3 normalWorld, vec3 positionWorld) {
    vec4 voxelDiffuse;
    for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
        VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];
        sampler3D grid = toSampler(voxelGrid.gridHandle);
        int gridSize = voxelGrid.resolution;
        float sceneScale = voxelGrid.scale;

        float minVoxelDiameter = sceneScale;
        float maxDist = 100;
        const int SAMPLE_COUNT = 7;

        for (int k = 0; k < SAMPLE_COUNT; k++) {
            const float PI = 3.1415926536;
            vec2 Xi = hammersley2d(k, SAMPLE_COUNT);
            float Phi = 2 * PI * Xi.x;
            float a = 0.5;
            float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )) );
            float SinTheta = sqrt( 1 - CosTheta * CosTheta );

            vec3 H;
            H.x = SinTheta * cos( Phi );
            H.y = SinTheta * sin( Phi );
            H.z = CosTheta;
            H = hemisphereSample_uniform(Xi.x, Xi.y, normalWorld);

            float coneRatio = 0.125 * sceneScale;
            float dotProd = clamp(dot(normalWorld, H),0,1);
            voxelDiffuse += vec4(dotProd) * voxelTraceCone(voxelGrid, grid, positionWorld, normalize(H), coneRatio, maxDist);
        }
    }

    return voxelDiffuse;
}
#else
vec4 traceVoxelsDiffuseBla(VoxelGridArray voxelGridArray, vec3 normalWorld, vec3 positionWorld) {
    return vec4(1,0,0,0);
}
#endif


void main()
{

    VoxelGridArray voxelGridArray;
    voxelGridArray.size = voxelGridCount;
    voxelGridArray.voxelGrids = voxelGrids;
    VoxelGrid grid = voxelGridArray.voxelGrids[voxelGridIndex];

	Material material = materials[g_materialIndex];
	vec3 materialDiffuseColor = material.diffuse;

    float opacity = 1-float(material.transparency);
	vec4 color = vec4(materialDiffuseColor, 1);
	float roughness = float(material.roughness);
	float metallic = float(material.metallic);


#if defined(BINDLESSTEXTURES) && defined(SHADER5)
    sampler2D diffuseMap;
    bool hasDiffuseMap = false;
    if(uint64_t(material.handleDiffuse) > 0) {
        diffuseMap = sampler2D(material.handleDiffuse);
        hasDiffuseMap = true;
    }
#endif

    if(hasDiffuseMap) {
        color = texture(diffuseMap, g_texcoord);
    }

    ivec3 positionGridSpace = worldToGridPosition(g_posWorld.xyz, grid);

//	imageStore(out_secondBounce, positionGridSpace, vec4(color.rgb*traceVoxelsDiffuseBla(voxelGridArray, normalize(g_normal), g_posWorld).rgb, opacity));
	imageStore(out_voxelAlbedo, positionGridSpace, vec4(color.rgb, opacity));
    imageStore(out_voxelNormal, positionGridSpace, vec4(normalize(g_normal), g_isStatic));
    imageStore(out_index, positionGridSpace, ivec4(g_entityIndex,0,0,0));
//    imageStore(out_index, positionGridSpace, ivec4(18,0,0,0));
//	TODO: Add emissive 0.25*float(material.ambient)
}
