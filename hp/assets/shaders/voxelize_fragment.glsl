layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding =3, rgba8) uniform image3D out_voxelNormal;
//layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding = 5, rgba8) uniform image3D out_voxel;
//layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform sampler2D shadowMap;
layout(binding=7) uniform sampler2D roughnessMap;
layout(binding=8) uniform sampler3D secondVoxelVolume;

flat in int f_axis;   //indicate which axis the projection uses
flat in vec4 f_AABB;

in vec3 g_normal;
in vec3 g_pos;
in vec2 g_texcoord;

//layout (pixel_center_integer) in vec4 gl_FragCoord;


//include(globals_structs.glsl)
//include(globals.glsl)


uniform int materialIndex;
uniform int entityIndex;
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
uniform int u_width;
uniform int u_height;

uniform float sceneScale = 1f;
uniform float inverseSceneScale = 1f;
uniform int gridSize = 256;

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

	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist <= moments.x) {
		//return vec3(1.0,1.0,1.0);
	}

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.0005);

	float d = dist - moments.x;
	//float p_max = (variance / (variance + d*d));
	// thanks, for light bleeding reduction, FOOGYWOO! http://dontnormalize.me/
	float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));

	p_max = smoothstep(0.1, 1.0, p_max);

	float darknessFactor = 420.0;
	p_max = clamp(exp(darknessFactor * (moments.x - dist)), 0.0, 1.0);

	//p_max = blurESM(shadowMap, ShadowCoordPostW.xy, dist, 0.002);

	return vec3(p_max,p_max,p_max);
}
void main()
{
	Material material = materials[materialIndex];
	vec3 materialDiffuseColor = vec3(material.diffuseR,
									 material.diffuseG,
									 material.diffuseB);

    float opacity = 1-float(material.transparency);
	vec4 color = vec4(materialDiffuseColor, 1);
	float roughness = float(material.roughness);
	float metallic = float(material.metallic);

	if(material.hasDiffuseMap != 0) {
        color = texture(diffuseMap, g_texcoord);
        metallic = color.a;
    }
	if(material.hasRoughnessMap != 0) {
        roughness = texture(roughnessMap, g_texcoord).r;
    }

    float glossiness = (1-roughness);
    vec3 maxSpecular = mix(vec3(0.1), color.rgb, metallic);
    vec3 specularColor = mix(vec3(0.02), maxSpecular, glossiness);

    const int gridSizeHalf = gridSize/2;
    vec3 gridPosition = vec3(inverseSceneScale)*g_pos.xyz + ivec3(gridSizeHalf);

    float ambientAmount = 0;//.0125f;
    float dynamicAdjust = 0;//.015f;
    vec3 voxelColor = color.rgb;
    vec3 voxelColorAmbient = (vec3(ambientAmount)+float((1+dynamicAdjust)*4*material.ambient))*voxelColor;

	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(g_pos.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow), 0, 1).r;

    float NdotL = max(0.5, clamp(dot(g_normal, lightDirection), 0, 1));

    vec3 finalVoxelColor = voxelColorAmbient+(NdotL*vec4(lightColor,1)*visibility*vec4(voxelColor,opacity)).rgb;

    vec4 diffuseVoxelTraced= traceVoxelsDiffuse(6, secondVoxelVolume, gridSize, sceneScale, g_normal, g_pos);
    vec4 voxelSpecular = voxelTraceCone(secondVoxelVolume, gridSize, sceneScale, 1, g_pos, normalize(g_normal), 0.1*float(material.roughness), 70); // 0.05
    vec3 maxMultipleBounce = vec3(0.5);
    finalVoxelColor += clamp(color.rgb*diffuseVoxelTraced.rgb + 0.25*specularColor * voxelSpecular.rgb, vec3(0,0,0), maxMultipleBounce);

	imageStore(out_voxel, ivec3(gridPosition), 0.25*vec4(finalVoxelColor, opacity));
	imageStore(out_voxelNormal, ivec3(gridPosition), vec4(g_normal, 0));
//    imageStore(out_voxel, ivec3(gridPosition), vec4(voxelColor, 1-alpha));
}
