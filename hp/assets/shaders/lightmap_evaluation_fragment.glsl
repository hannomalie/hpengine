#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=5) uniform sampler2D lastFrameFinalBuffer;
layout(binding=6) uniform samplerCube globalEnvironmentMap;
layout(binding=7) uniform sampler2D lastFrameReflectionBuffer;
layout(binding=8) uniform samplerCubeArray probes;
layout(binding=9) uniform sampler2D lightmap;
layout(binding = 10) uniform samplerCube environmentMap;
layout(binding=11) uniform sampler2D ambientLightMap; // diffuse, specular
layout(binding=12) uniform sampler2D lightmapUV; // diffuse, specular

layout(std430, binding=0) buffer myBlock
{
  float exposure;
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform bool useAmbientOcclusion = true;
uniform bool useSSR = true;
uniform int currentProbe;
uniform bool secondBounce = false;
uniform int N = 12;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];
uniform float environmentMapWeights[100];

uniform samplerCube handle;

uniform int countX;
uniform int countY;
uniform int countZ;

uniform samplerCube handles[4][4][4];

uniform int probeSize;

in vec2 pass_TextureCoord;

layout(location=0)out vec4 out_environment;
layout(location=1)out vec4 out_refracted;


//include(globals.glsl)


const float blurDistance = 0.025;
const vec2 offsets[9] = { vec2(-blurDistance, -blurDistance),
					vec2(0, -blurDistance),
					vec2(blurDistance, -blurDistance),
					vec2(-blurDistance, 0),
					vec2(0, 0),
					vec2(blurDistance, 0),
					vec2(-blurDistance, blurDistance),
					vec2(0, blurDistance),
					vec2(blurDistance, blurDistance)
};

vec4 bilateralBlur(sampler2D sampler, vec2 texCoords) {

	vec4 result = vec4(0,0,0,0);
	float normalization = 0;

	vec4 centerSample = textureLod(sampler, texCoords + offsets[4], 0);
	float centerSampleDepth = textureLod(normalMap, texCoords + offsets[4], 0).a;
	result += kernel[4] * centerSample;

	for(int i = 0; i < 9; i++) {
		if(i == 4) { continue; }

		vec4 currentSample = textureLod(sampler, texCoords + offsets[i], 0);
		float currentSampleDepth = textureLod(normalMap, texCoords + offsets[i], 0).a;

		float closeness = 1-distance(currentSampleDepth, centerSampleDepth);
		float sampleWeight = kernel[i] * closeness;
		result += sampleWeight * currentSample;

		normalization += (1-closeness)*kernel[i]; // this is the amount we have lost.
	}

	return result + normalization * centerSample;
}

samplerCube samplerForPosition(vec3 positionWorld, int countX, int countY, int countZ) {
    positionWorld *= 1f/float(probeSize);

    ivec3 positionForIndex = ivec3(positionWorld) + ivec3(0.5f*float(countX), 0.5f*float(countY), 0.5f*float(countZ));
    positionForIndex.x = max(min(positionForIndex.x, countX), 0);
    positionForIndex.y = max(min(positionForIndex.y, countY), 0);
    positionForIndex.z = max(min(positionForIndex.z, countZ), 0);
    return handles[positionForIndex.x][positionForIndex.y][positionForIndex.z];
}

void main()
{
	vec2 st = pass_TextureCoord;
	vec4 positionViewRoughness = textureLod(positionMap, st, 0);
	vec4 lightmapUVs = textureLod(lightmapUV, st, 0);
	vec3 positionView = positionViewRoughness.rgb;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 normalView = textureLod(normalMap, st, 0).rgb;
  	vec3 normalWorld = normalize(inverse(viewMatrix) * vec4(normalView,0)).xyz;
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	V = normalize(inverse(viewMatrix) * vec4(positionView,0)).xyz;
	vec4 colorMetallic = textureLod(diffuseMap, st, 0);
	vec3 color = colorMetallic.rgb;
	float roughness = positionViewRoughness.a;
	float metallic = colorMetallic.a;

	vec3 result = vec3(0,0,0);
    samplerCube probe = samplerForPosition(positionWorld+normalWorld, countX, countY, countZ);

    vec4 probeSample = textureLod(probe, normalWorld, 0);
    vec2 lightmapCoords = probeSample.rg;
    vec3 lightmapSample = textureLod(lightmap, lightmapCoords, 0).rgb;
    result += lightmapSample;

    const vec3[6] sampleVectors = { vec3(0,0,1),
                                    vec3(0,1,0),
                                    vec3(1,0,0),
                                    vec3(0,0,-1),
                                    vec3(0,-1,0),
                                    vec3(-1,0,0),
                                    };
    for(int i = 0; i < 6; i++) {
        vec2 _lightmapCoords = textureLod(probe, sampleVectors[i], 10).rg;
        vec3 _lightmapSample = textureLod(lightmap, _lightmapCoords, 0).rgb;
        result += _lightmapSample;// * clamp(dot(sampleVectors[i], normalWorld), 0., 1.);
    }
    result /= 6;

//    result = vec3(1,0, 0);
//    result = vec3(lightmapCoords, 0);
//    result = vec3(lightmapSample);
//    result = probeSample.xyz;
//	out_environment.rgb = result;
    out_environment = vec4(clamp(textureLod(lightmap, lightmapUVs.xy, 0).rgb, 0, 1),0.25f);
    const bool debugLightmap = false;
    if(debugLightmap) {
        float g = 0;
        if (int(lightmapUVs.x*textureSize(lightmap,0).x) % 2 == 0) {
            if(int(lightmapUVs.y*textureSize(lightmap,0).y) % 2 == 0) {
                g = 0.01f;
            }
        } else {
            if(int(lightmapUVs.y*textureSize(lightmap,0).y) % 2 != 0) {
                g = 0.01f;
            }
        }
        out_environment = vec4(0, 0, g, 0.25);
    }
//    vec4 bilateralBlurredSample = bilateralBlur(lightmap, lightmapUVs.xy);
//    out_environment = 4*vec4(bilateralBlurredSample.rgb, 1);
}
