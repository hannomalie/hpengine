layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3, rgba8) uniform image3D out_voxelNormal;
//layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5, rgba8) uniform image3D out_voxelAlbedo;
layout(binding=6, rgba8) uniform image3D out_secondBounce;
//layout(binding=5) uniform sampler2D reflectionMap;
//layout(binding=6) uniform sampler2D shadowMap;
layout(binding=7) uniform sampler2D roughnessMap;
layout(binding=8) uniform sampler3D secondVoxelVolume;

flat in int g_axis;   //indicate which axis the projection uses
flat in vec4 g_AABB;

in vec3 g_normal;
in vec3 g_pos;
in vec3 g_posWorld;
in vec2 g_texcoord;
flat in int g_materialIndex;
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
    VoxelGridArray voxelGridArray;
};

uniform mat4 shadowMatrix;

uniform vec3 lightDirection;
uniform vec3 lightColor;

uniform int voxelGridIndex = 0;


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


void main()
{

    VoxelGrid grid = voxelGridArray.voxelGrids[voxelGridIndex];

	Material material = materials[g_materialIndex];
	vec3 materialDiffuseColor = vec3(material.diffuseR,
									 material.diffuseG,
									 material.diffuseB);

    float opacity = 1-float(material.transparency);
	vec4 color = vec4(materialDiffuseColor, 1);
	float roughness = float(material.roughness);
	float metallic = float(material.metallic);

	if(uint64_t(material.handleDiffuse) > 0) {
        sampler2D _diffuseMap = sampler2D((material.handleDiffuse));
        color = texture(_diffuseMap, g_texcoord);
    }
	if(uint64_t(material.handleRoughness) > 0) {
        sampler2D _handleRoughnessMap = sampler2D((material.handleRoughness));
        roughness = texture(_handleRoughnessMap, g_texcoord).r;
    }

    ivec3 positionGridSpace = worldToGridPosition(g_posWorld.xyz, grid);

	imageStore(out_secondBounce, positionGridSpace, vec4(color.rgb*traceVoxelsDiffuseBla(voxelGridArray, normalize(g_normal), g_posWorld).rgb, opacity));
	imageStore(out_voxelAlbedo, positionGridSpace, vec4(color.rgb, opacity));
	imageStore(out_voxelNormal, positionGridSpace, vec4(normalize(g_normal), g_isStatic));
//	TODO: Add emissive 0.25*float(material.ambient)
}
