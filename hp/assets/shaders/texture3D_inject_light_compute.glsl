#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable
#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0, rgba8) writeonly uniform image3D targetVoxelGrid;
layout(binding=6) uniform sampler2D shadowMap;

uniform mat4 shadowMatrix;

uniform vec3 lightDirection;
uniform vec3 lightColor;

uniform int bounces = 1;
//include(globals_structs.glsl)
//include(globals.glsl)

uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};

layout(std430, binding=5) buffer _voxelGrids {
	VoxelGrid voxelGrids[10];
};

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

	// Surface is fully lit. as the current fragment is before the lights occluder
	if (dist <= moments.x + 0.001) {
		return vec3(1.0,1.0,1.0);
	}
	else { return vec3(0.); }

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

float calculateAttenuation(float dist, float lightRadius) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}
void main(void) {
    VoxelGrid voxelGrid = voxelGrids[0];
    float sceneScale = voxelGrid.scale;
    float inverseSceneScale = 1f/sceneScale;

    sampler3D albedoGrid = sampler3D(uint64_t(voxelGrid.albedoGridHandle));
    sampler3D normalGrid = sampler3D(uint64_t(voxelGrid.normalGridHandle));
    sampler3D secondVoxelGrid = sampler3D(uint64_t(voxelGrid.grid2Handle));

	ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
	ivec3 workGroup = ivec3(gl_WorkGroupID);
	ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
	ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);

	vec4 sourceValue = vec4(0);//imageLoad(source, loadPosition);
	float weightSum = 0;

	float visibility = 1.0;
	vec3 positionWorld = sceneScale*vec3(storePos-vec3(float(voxelGrid.resolutionHalf)));
	vec3 gridPosition = vec3(inverseSceneScale)*positionWorld.xyz + ivec3(voxelGrid.resolutionHalf);
    vec3 positionGridScaled = inverseSceneScale*gridPosition.xyz;
    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(voxelGrid.resolution)+vec3(0.5);

    vec4 color = texelFetch(albedoGrid, storePos, 0);
    vec4 normalStaticEmissive = texelFetch(normalGrid, storePos, 0);
    vec3 g_normal = normalize(Decode(normalStaticEmissive.xy));
    vec3 g_pos = positionWorld;
    float opacity = color.a;
    float isStatic = normalStaticEmissive.b;

    //second bounce?
    vec3 ambientAmount = vec3(0);
    float dynamicAdjust = 0;//.015f;
    vec3 voxelColor = color.rgb;
    vec3 voxelColorAmbient = (vec3(ambientAmount)+float(normalStaticEmissive.a))*voxelColor;

	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(getVisibility(depthInLightSpace, positionShadow), 0.0, 1.0).r;

	vec3 lightDirectionTemp = lightDirection;
    float NdotL = max(0.1, clamp(dot(g_normal, normalize(lightDirectionTemp)), 0.0, 1.0));

    vec3 finalVoxelColor = voxelColorAmbient+(NdotL*vec4(lightColor,1)*visibility*vec4(voxelColor,1)).rgb;

    for(int i = 0; i < pointLightCount; i++) {
        PointLight pointLight = pointLights[i];
        vec3 lightPosition = pointLight.position;
        vec3 lightDiffuse = pointLight.color;
        vec3 lightDirection = normalize(vec4(lightPosition - positionWorld, 0)).xyz;
        float attenuation = calculateAttenuation(length(lightPosition - positionWorld.xyz), float(pointLight.radius));

        finalVoxelColor += 4*attenuation*clamp(dot(lightDirection, g_normal), 0, 1) * lightDiffuse * voxelColor*0.1;
    }

	imageStore(targetVoxelGrid, storePos, vec4(finalVoxelColor, opacity));
}
