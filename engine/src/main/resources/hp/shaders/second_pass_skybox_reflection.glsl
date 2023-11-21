#define WORK_GROUP_SIZE 16

//include(globals_structs.glsl)

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4, rgba16f) uniform image2D out_Diffuse;
layout(binding=5) uniform sampler2D visibilityMap;
layout(binding=6) uniform samplerCube skyBox;
layout(binding=7, rgba16f) uniform image2D out_Specular;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;


layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _directionalLightState {
	DirectionalLightState directionalLight;
};

//include(globals.glsl)
//include(global_lighting.glsl)

void main(void) {
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	vec2 st = vec2(storePos) / vec2(screenWidth, screenHeight);

	vec3 positionView = textureLod(positionMap, st, 0).xyz;
	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;

	vec3 color = textureLod(diffuseMap, st, 0).xyz;
	float roughness = textureLod(positionMap, st, 0).w;
	float metallic = textureLod(diffuseMap, st, 0).w;

	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, roughness);
	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	vec3 normalView = textureLod(normalMap, st, 0).xyz;
	vec3 normalWorld = normalize(inverse(viewMatrix) * vec4(normalView,0)).xyz;
	vec4 specular = textureLod(specularMap, st, 0);
	float depthFloat = textureLod(normalMap, st, 0).w;
	depthFloat = textureLod(visibilityMap, st, 0).g;


	vec3 normal = boxProject(positionWorld, normalWorld, vec3(-500), vec3(500));
	vec3 reflectedNormal = boxProject(positionWorld, reflect(V, normalWorld), vec3(-500), vec3(500));

	vec4 resultDiffuse = vec4(textureLod(skyBox, normal, 8).rgb, 0);
	vec4 resultSpecular = vec4(textureLod(skyBox, reflectedNormal, roughness * 8).rgb, 0);

	imageStore(out_Diffuse, storePos, resultDiffuse);
	imageStore(out_Specular, storePos, resultSpecular);

}
