
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
layout(std430, binding=5) buffer _probeAmbientCubeValues {
	vec4 probeAmbientCubeValues[];
};
in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_indirectDiffuse;

const vec3[6] directions = {
	vec3(1, 0, 0),
	vec3(-1, 0, 0),
	vec3(0, 1, 0),
	vec3(0, -1, 0),
	vec3(0, 0, 1),
	vec3(0, 0, -1)
};

/*
morton order xn=3, yn=2, zn=4:
000
001
002
003
010
011
012
013
100
101
102
103
110
111
112
113
200
201
202
203
210
211
212
213
*/

vec3 toGridSpace(vec3 positionWorld, vec3 sceneMin, vec3 probeDimensions) {
	return (positionWorld-sceneMin)/probeDimensions; // +probeDimensions*0.5f ???
}

int getProbeIndexForGridCoords(ivec3 probeGridCoords, ivec3 probesPerDimensionInt) {
	int resultingProbeIndex = probeGridCoords.x * (probesPerDimensionInt.y * probesPerDimensionInt.z)
		+ probeGridCoords.y * probesPerDimensionInt.z
		+ probeGridCoords.z;
	return resultingProbeIndex;
}

int retrieveProbeIndex(vec3 positionWorld, vec3 sceneMin, vec3 probeDimensions) {
	ivec3 probeGridCoords = ivec3(toGridSpace(positionWorld, sceneMin, probeDimensions));
	ivec3 probesPerDimensionInt = ivec3(probesPerDimension);
	return getProbeIndexForGridCoords(probeGridCoords, probesPerDimensionInt);
}

vec4 sampleProbe(int probeIndex, vec3 normalWorld) {
	int baseProbeIndex = probeIndex*6;
	vec4 result = vec4(0);
	for(int faceIndex = 0; faceIndex < 6; faceIndex++) {
		float dotProd = clamp(dot(normalWorld, directions[faceIndex]), 0, 1);
		result += dotProd * probeAmbientCubeValues[baseProbeIndex+faceIndex];
	}
	return result;
}
vec3 roundUp(vec3 value) {
	return sign(value)*ceil(abs(value));
}

vec3 getD(vec3 position, vec3 min, vec3 max) {
	return (position-min) / (max-min);
}
void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
	vec3 probeDimensionsHalf = probeDimensions * 0.5f;

	vec3 positionView = textureLod(positionMap, st, 0).xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1.0f)).xyz;

	vec4 normalAmbient = textureLod(normalMap, st, 0);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = (inverse(viewMatrix) * vec4(normalView, 0.0f)).xyz;

	int probeIndex = retrieveProbeIndex(positionWorld, sceneMin, probeDimensions);
	vec3 probe = sampleProbe(probeIndex, normalWorld).rgb;
	vec3 probePosition = probePositions[probeIndex].xyz;
	vec3 result = probe;

	///////////

	vec3 positionGridSpace = toGridSpace(positionWorld, sceneMin, probeDimensions);
	vec3 positionGridSpaceFloored = floor(positionGridSpace);
	vec3 positionGridSpaceRoundUp = roundUp(positionGridSpace);

	ivec3 probesPerDimensionInt = ivec3(probesPerDimension);

	vec3 c000 = vec3(positionGridSpaceFloored.xyz);
	vec3 c001 = vec3(positionGridSpaceFloored.x, positionGridSpaceRoundUp.y, positionGridSpaceFloored.z);
	vec3 c100 = vec3(positionGridSpaceRoundUp.x, positionGridSpaceFloored.yz);
	vec3 c101 = vec3(positionGridSpaceRoundUp.x, positionGridSpaceRoundUp.y, positionGridSpaceFloored.z);

	vec3 c010 = vec3(positionGridSpaceFloored.xy, positionGridSpaceRoundUp.z);
	vec3 c011 = vec3(positionGridSpaceFloored.x, positionGridSpaceRoundUp.yz);
	vec3 c110 = vec3(positionGridSpaceRoundUp.x, positionGridSpaceFloored.y, positionGridSpaceRoundUp.z);
	vec3 c111 = vec3(positionGridSpaceRoundUp.x, positionGridSpaceRoundUp.y, positionGridSpaceRoundUp.z);

	vec3 c000Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c000), probesPerDimensionInt), normalWorld).rgb;
	vec3 c001Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c001), probesPerDimensionInt), normalWorld).rgb;
	vec3 c100Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c100), probesPerDimensionInt), normalWorld).rgb;
	vec3 c101Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c101), probesPerDimensionInt), normalWorld).rgb;
	vec3 c010Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c010), probesPerDimensionInt), normalWorld).rgb;
	vec3 c011Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c011), probesPerDimensionInt), normalWorld).rgb;
	vec3 c110Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c110), probesPerDimensionInt), normalWorld).rgb;
	vec3 c111Probe = sampleProbe(getProbeIndexForGridCoords(ivec3(c111), probesPerDimensionInt), normalWorld).rgb;

	vec3 d = getD(positionGridSpace, c000, c111);

	vec3 c00 = (1 - d.x) * c000Probe + d.x * c100Probe;
	vec3 c01 = (1 - d.x) * c001Probe + d.x * c101Probe;
	vec3 c10 = (1 - d.x) * c010Probe + d.x * c110Probe;
	vec3 c11 = (1 - d.x) * c011Probe + d.x * c111Probe;

	vec3 c0 = (1 - d.y) * c00 + d.y * c01;
	vec3 c1 = (1 - d.y) * c10 + d.y * c11;

	vec3 c = (1 - d.z) * c0 + d.z * c1;

	result = vec3(c);

	vec3 positionToProbe = positionWorld - probePosition;
	float NdotL = clamp(dot(positionToProbe, normalWorld), 0, 1);

	result *= NdotL;

	out_indirectDiffuse.rgb = result;
}
