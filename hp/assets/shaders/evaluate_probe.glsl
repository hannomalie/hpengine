
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal

layout(binding=8) uniform samplerCubeArray probeCubeMaps;
layout(binding=13) uniform sampler3D grid;

//include(globals_structs.glsl)
//include(globals.glsl)

uniform float screenWidth = 1280/2;
uniform float screenHeight = 720/2;

uniform int time = 0;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 eyePosition = vec3(0);
uniform int probeCount = 64;
uniform vec3 sceneCenter = vec3(0);
uniform vec3 sceneMin = vec3(0);
uniform vec3 probesPerDimension = vec3(4);
uniform vec3 probeDimensions = vec3(50);

layout(std430, binding=4) buffer _probePositions {
	vec4 probePositions[];
};
in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_indirectDiffuse;

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
	vec3 probeDimensionsHalf = probeDimensions * 0.5f;

	vec3 positionView = textureLod(positionMap, st, 0).xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1.0f)).xyz;

	vec4 normalAmbient = textureLod(normalMap, st, 0);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView, 0.0f)).xyz;

	vec4 result = vec4(0,0,0,0);
	ivec3 probeIndexOffsets = ivec3(positionWorld-sceneMin)/ivec3(probeDimensions);
	ivec3 probesPerDimensionInt = ivec3(probesPerDimension);
	int resultingProbeIndex = probesPerDimensionInt.x * probeIndexOffsets.x
							+ probesPerDimensionInt.y * probeIndexOffsets.y
							+ probesPerDimensionInt.z * probeIndexOffsets.z;

	float mipMap = float(textureQueryLevels(probeCubeMaps)-2); // TODO: Figure out why -2
	result.rgb += textureLod(probeCubeMaps, vec4(normalWorld, resultingProbeIndex), mipMap).rgb;

//	for(int i = 0; i < probeCount; i++) {
//		vec3 currentProbeMin = probePositions[i].xyz - probeDimensionsHalf;
//		vec3 currentProbeMax = probePositions[i].xyz + probeDimensionsHalf;
//		bool greaterThanMin = all(greaterThanEqual(positionWorld, currentProbeMin));
//		bool lessThanMax = all(lessThan(positionWorld, currentProbeMax));
//		if(greaterThanMin && lessThanMax)
//		{
//			float mipMap = float(textureQueryLevels(probeCubeMaps)-2); // TODO: Figure out why -2
//			result.rgb += 0.1f*textureLod(probeCubeMaps, vec4(normalWorld, i), mipMap).rgb;
//			break;
//		}
//	}
	out_indirectDiffuse = result;
}
