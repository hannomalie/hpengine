#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3, rgba8) uniform image3D out_voxelNormal;
//layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5, rgba8) uniform image3D out_voxelAlbedo;
//layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform sampler2D shadowMap;
layout(binding=7) uniform sampler2D roughnessMap;
layout(binding=8) uniform sampler3D secondVoxelVolume;

flat in int g_axis;   //indicate which axis the projection uses
flat in vec4 g_AABB;

in vec3 g_normal;
in vec3 g_pos;
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
	VoxelGrid voxelGrids[10];
};

uniform mat4 shadowMatrix;

uniform vec3 lightDirection;
uniform vec3 lightColor;

vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
  		float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
		return vec3(0,0,0);
	}

	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 2);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;

//	moments = blur(shadowMap, ShadowCoordPostW.xy, 0.0125, 1).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.0017).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;
	//moments /= 3;

	// Surface is fully lit. as the current fragment is before the lights occluder
	if (dist <= moments.x) {
//		return vec3(1.0,1.0,1.0);
	}

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.0005);

	float d = dist - moments.x;
	//float p_max = (variance / (variance + d*d));
	// thanks, for lights bleeding reduction, FOOGYWOO! http://dontnormalize.me/
	float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));

	p_max = smoothstep(0.1, 1.0, p_max);

	float darknessFactor = 420.0;
	p_max = clamp(exp(darknessFactor * (moments.x - dist)), 0.0, 1.0);

	//p_max = blurESM(shadowMap, ShadowCoordPostW.xy, dist, 0.002);

	return vec3(p_max,p_max,p_max);
}
void main()
{
//	if( g_pos.x < g_AABB.x || g_pos.y < g_AABB.y || g_pos.x > g_AABB.z || g_pos.y > g_AABB.w )
//		discard;


    VoxelGrid grid = voxelGrids[0];
    float sceneScale = grid.scale;
    float inverseSceneScale = 1f / grid.scale;
    int gridSize = grid.resolution;


	Material material = materials[g_materialIndex];
	vec3 materialDiffuseColor = vec3(material.diffuseR,
									 material.diffuseG,
									 material.diffuseB);

    float opacity = 1-float(material.transparency);
	vec4 color = vec4(materialDiffuseColor, 1);
	float roughness = float(material.roughness);
	float metallic = float(material.metallic);

	if(material.hasDiffuseMap != 0) {
        sampler2D _diffuseMap = sampler2D(uint64_t(material.handleDiffuse));
        color = texture(_diffuseMap, g_texcoord);
    }
	if(material.hasRoughnessMap != 0) {
        roughness = texture(roughnessMap, g_texcoord).r;
    }

    float glossiness = (1-roughness);
    vec3 maxSpecular = mix(vec3(0.1), color.rgb, metallic);
    vec3 specularColor = mix(vec3(0.02), maxSpecular, glossiness);

    const int gridSizeHalf = gridSize/2;
    vec3 gridPosition = vec3(inverseSceneScale) * g_pos.xyz + vec3(gridSizeHalf);

//    vec3 samplePositionNormalized = gridPosition/vec3(gridSize);
//    if(textureLod(secondVoxelVolume, samplePositionNormalized, 0).b > 0) { discard; }

    float ambientAmount = 0.1;//.0125f;
    float dynamicAdjust = 0;//.015f;
    vec3 voxelColor = color.rgb;

	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(g_pos.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow), 0, 1).r;

	imageStore(out_voxelAlbedo, ivec3(round(gridPosition)), vec4(color.rgb, opacity));
	imageStore(out_voxelNormal, ivec3(round(gridPosition)), vec4(Encode(normalize(g_normal)), g_isStatic, 0.25*float(material.ambient)));
//    imageStore(out_voxelAlbedo, ivec3(round(gridPosition)), vec4(vec3(0,0,1) ,1));
}
