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

//	moments = blur(shadowMap, ShadowCoordPostW.xy, 0.0125, 1).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.0017).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;
	//moments /= 3;

	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist <= moments.x) {
		return vec3(1.0,1.0,1.0);
	}
	else { return vec3(0); }

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
	vec3 gridPosition = vec3(inverseSceneScale)*positionWorld.xyz + ivec3(gridSizeHalf);
    vec3 positionGridScaled = inverseSceneScale*gridPosition.xyz;
    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(gridSize)+vec3(0.5);

    vec4 color = texelFetch(albedoGrid, storePos, 0);//voxelFetch(albedoGrid, gridSize, sceneScale, positionWorld, 0);
    vec4 normalStaticEmissive = texelFetch(normalGrid, storePos, 0);//voxelFetch(normalGrid, gridSize, sceneScale, positionWorld, 0);
    vec3 g_normal = normalize(Decode(normalStaticEmissive.xy));
    vec3 g_pos = positionWorld;
    float opacity = color.a;
    float isStatic = normalStaticEmissive.b;

    float ambientAmount = 0;//.0125f;
    float dynamicAdjust = 0;//.015f;
    vec3 voxelColor = color.rgb;
    vec3 voxelColorAmbient = (vec3(ambientAmount)+float(normalStaticEmissive.a))*voxelColor;

	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(getVisibility(depthInLightSpace, positionShadow), 0, 1).r;
//	visibility = 1-visibility;

	vec3 lightDirectionTemp = lightDirection;
//	lightDirectionTemp.z *=-1;
    float NdotL = max(0.0, clamp(dot(g_normal, normalize(lightDirectionTemp)), 0.0, 1.0));

    vec3 finalVoxelColor = voxelColorAmbient+(NdotL*vec4(lightColor,1)*visibility*vec4(voxelColor,opacity)).rgb;

    const int SAMPLE_COUNT = 4;
    vec4 diffuseVoxelTraced = traceVoxelsDiffuse(SAMPLE_COUNT, secondVoxelGrid, gridSize, sceneScale, g_normal, g_pos);
    vec4 voxelSpecular = 0.25*voxelTraceCone(secondVoxelGrid, gridSize, sceneScale, sceneScale, g_pos, normalize(g_normal), 0.85, 70); // 0.05
    vec3 maxMultipleBounce = vec3(.5);
	finalVoxelColor += 0.5*clamp(color.rgb*diffuseVoxelTraced.rgb + color.rgb * voxelSpecular.rgb, vec3(0,0,0), maxMultipleBounce);


	imageStore(voxelGrid, storePos, vec4(finalVoxelColor, opacity));
}
