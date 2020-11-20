
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal

layout(binding=7) uniform samplerCubeArray probeCubeMaps;

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
uniform vec3 probeDimensions = vec3(50);

layout(std430, binding=4) buffer _probeMinMax {
	vec4 probeMinMax[];
};

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_indirectDiffuse;
layout(location=1)out vec4 out_indirectSpecular;

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

	vec3 resultSpecular = vec3(0,0,0);
	vec3 resultDiffuse = vec3(0,0,0);

	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;

	for(int probeIndex = 0; probeIndex < probeCount; probeIndex++) {
		vec3 probeMin = probeMinMax[2*probeIndex].xyz;
		vec3 probeMax = probeMinMax[2*probeIndex+1].xyz;
		vec3 probeExtents = probeMax - probeMin;
		vec3 probePosition = probeMin + 0.5*probeExtents;

		if(isInside(positionWorld, probeMin, probeMax)) {

			vec3 normal = normalWorld;
			normal = boxProject(positionWorld, normal, probeMin, probeMax);
			resultDiffuse += textureLod(probeCubeMaps, vec4(normal, probeIndex), 8).rgb;

			vec3 reflectedNormal = reflect(V, normalWorld);
			reflectedNormal = boxProject(positionWorld, reflectedNormal, probeMin, probeMax);
			resultSpecular += textureLod(probeCubeMaps, vec4(reflectedNormal, probeIndex), 0).rgb;
		}
	}

	out_indirectSpecular.rgb = resultSpecular;
	out_indirectDiffuse.rgb = resultDiffuse;
}
