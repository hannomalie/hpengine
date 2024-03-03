#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D displacementMap;
uniform bool hasDisplacementMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif
layout(binding=6) uniform samplerCube environmentMap;

uniform bool isSelected = false;

uniform bool useParallax;
uniform bool useSteepParallax;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

uniform vec3 eyePosition;

uniform bool useNormalMaps = true;

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_color;

//include(globals.glsl)
//include(normals.glsl)

void main(void) {
	vec4 position_world = vertexShaderOutput.position_world;
	vec4 position_clip = vertexShaderOutput.position_clip;
	vec4 position_clip_last = vertexShaderOutput.position_clip_last;
	vec3 normal_world = vertexShaderOutput.normal_world;

	vec3 V = -normalize((position_world.xyz + eyePosition.xyz).xyz);

	vec4 position_clip_post_w = position_clip/position_clip.w;
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 motionVec = (position_clip_post_w.xy) - (position_clip_last_post_w.xy);

	float depth = (position_clip.z / position_clip.w);

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

	out_color = vec4(textureLod(environmentMap, V, 0).rgb, 1);
}
