#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) writeonly uniform image3D voxelGrid;
layout(binding=1) uniform sampler3D albedoGrid;
layout(binding=2) uniform sampler3D normalGrid;
layout(binding=3) uniform sampler3D secondVoxelGrid;
layout(binding=6) uniform sampler2D shadowMap;

uniform float sceneScale = 2f;
uniform float inverseSceneScale = 0.5f;
uniform int gridSize = 256;

uniform mat4 shadowMatrix;

uniform vec3 lightDirection;
uniform vec3 lightColor;

uniform int bounces = 1;
uniform int lightInjectedFramesAgo = 1;

//include(globals_structs.glsl)
//include(globals.glsl)

vec3 getVisibility(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
//  		float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
		return vec3(0,0,0);
	}

	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 2);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;

	if (dist <= moments.x) {
		return vec3(1.0,1.0,1.0);
	}
	else { return vec3(0); }

}

void main(void) {
    int gridSizeHalf = gridSize/2;
	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);

	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
	float weightSum = 0;

	float visibility = 1.0;
	vec3 positionWorld = sceneScale*vec3(storePos-vec3(float(gridSizeHalf)));

    vec4 color = texelFetch(albedoGrid, storePos, 0);//voxelFetch(albedoGrid, gridSize, sceneScale, positionWorld, 0);
    vec4 normalStaticEmissive = texelFetch(normalGrid, storePos, 0);//voxelFetch(normalGrid, gridSize, sceneScale, positionWorld, 0);
    vec3 normalWorld = normalize(Decode(normalStaticEmissive.xy));
    float opacity = color.a;
    float emissive = normalStaticEmissive.a;

	vec4 currentPositionsValues = texelFetch(secondVoxelGrid, storePos,0);
    vec3 finalVoxelColor = currentPositionsValues.rgb;

    vec4 diffuseVoxelTraced = traceVoxelsDiffuse(secondVoxelGrid, gridSize, sceneScale, normalWorld, positionWorld+normalWorld*sceneScale);

    vec3 maxMultipleBounce = vec3(0.1f);
	vec3 multipleBounceColor = maxMultipleBounce*(diffuseVoxelTraced.rgb);

	finalVoxelColor.rgb += color.rgb*multipleBounceColor.rgb;

	imageStore(voxelGrid, storePos, vec4(finalVoxelColor, currentPositionsValues.a));
}
