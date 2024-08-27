#define WORK_GROUP_SIZE 4

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding=0) uniform sampler2D visibilityTexture;
layout(binding=1) uniform sampler2D depthexture;
layout(binding=2) uniform sampler2DArray diffuseTextures;
layout(binding=3) uniform sampler2D normalTexture;
layout(binding=8) uniform samplerCubeArray pointLightShadowMapsCube;
#ifdef BINDLESSTEXTURES
#else
layout(binding=9) uniform sampler2D directionalLightShadowMap;
layout(binding=10) uniform sampler2D directionalLightStaticShadowMap;
#endif
layout(binding=3, rgba8) uniform image2D out_color;

//include(globals_structs.glsl)
//include(globals.glsl)
//include(global_lighting.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _entities {
	Entity entities[1000];
};
layout(std430, binding=3) buffer _pointLights {
	PointLight pointLights[1000];
};
layout(std430, binding=4) buffer _directionalLight {
	DirectionalLightState directionalLight;
};

uniform int width = 1920;
uniform int height = 1080;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform int pointLightCount = 0;

// https://stackoverflow.com/questions/32227283/getting-world-position-from-depth-buffer-value
vec3 worldPosFromDepth(float depth, vec2 st) {
	float z = depth * 2.0 - 1.0;

	vec4 clipSpacePosition = vec4(st * 2.0 - 1.0, z, 1.0);
	vec4 viewSpacePosition = inverse(projectionMatrix) * clipSpacePosition;

	// Perspective division
	viewSpacePosition /= viewSpacePosition.w;

	vec4 worldSpacePosition = inverse(viewMatrix) * viewSpacePosition;

	return worldSpacePosition.xyz;
}
// https://stackoverflow.com/questions/10786951/omnidirectional-shadow-mapping-with-depth-cubemap
float VectorToDepthValue(vec3 Vec, float far) {
	vec3 AbsVec = abs(Vec);
	float LocalZcomp = max(AbsVec.x, max(AbsVec.y, AbsVec.z));

	const float n = 0.1;
	float NormZComp = (far+n) / (far-n) - (2*far*n)/(far-n)/LocalZcomp;
	return (NormZComp + 1.0) * 0.5;
}
float getVisibilityCubemap(vec3 positionWorld, uint pointLightIndex, PointLight pointLight) {
	vec3 pointLightPositionWorld = pointLight.position;

	vec3 fragToLight = positionWorld - pointLightPositionWorld;
	vec4 textureSample = textureLod(pointLightShadowMapsCube, vec4(fragToLight, pointLightIndex), 0);
	float closestDepth = textureSample.r;
	vec2 moments = textureSample.xy;
	float currentDepth = VectorToDepthValue(fragToLight, pointLight.radius);
	float bias = 0.0002;
	float shadow = currentDepth - bias < closestDepth ? 1.0 : 0.0;

	return shadow;
}

void main(void) {
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	ivec2 size = ivec2(width, height);
	vec2 st = vec2(storePos) / vec2(size);

	float depth = textureLod(depthexture, st, 0).r;
	vec3 positionWorld = worldPosFromDepth(depth, st);
	vec3 positionView = (viewMatrix * vec4(positionWorld, 1)).xyz;
	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	vec4 visibilitySample = textureLod(visibilityTexture, st, 0);
	vec4 normalSample = textureLod(normalTexture, st, 0);
	vec3 normalWorld = normalSample.xyz;
	vec3 normalView = (viewMatrix * vec4(normalWorld, 0)).xyz;
	vec2 uv = visibilitySample.rg;
	float mipMapLevel = visibilitySample.b;
	int entityId = int(visibilitySample.a);

	Entity entity = entities[entityId];
	Material material = materials[entity.materialIndex];
	int materialType = int(material.materialtype);
	int DEFAULT = 0;
	int FOLIAGE = 1;
	int UNLIT = 2;

	vec3 color = material.diffuse;
	float roughness = 1;
	float metallic = 0;
	vec3 diffuseColor = color.rgb;
	vec3 specularColor = diffuseColor;


#ifdef BINDLESSTEXTURES
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) {
	    sampler2D diffuseMap  = sampler2D(material.handleDiffuse);
		color.rgb = textureLod(diffuseMap, uv, mipMapLevel).rgb;
	}
#else
	color.rgb = textureLod(diffuseTextures, vec3(uv, material.diffuseMapIndex), mipMapLevel).rgb;
#endif

	vec3 finalColor;

//	TODO: This causes unaccaptable crashes on linux...
/*
	for (uint lightIndex = 0; lightIndex < pointLightCount; ++lightIndex)
	{
		PointLight pointLight = pointLights[lightIndex];
		vec3 pointLightPosition = pointLight.position;
		vec4 pos = (viewMatrix * vec4(pointLightPosition, 1.0f));
		float rad = 2*float(pointLight.radius);

		if (distance(pointLight.position, positionWorld) < rad) {

			vec3 lightPositionView = (viewMatrix * vec4(pointLight.position, 1)).xyz;
			vec3 lightDiffuse = pointLight.color;
			vec3 lightDirectionView = normalize(vec4(lightPositionView - positionView, 0)).xyz;
			float attenuation = calculateAttenuation(length(lightPositionView - positionView), float(pointLight.radius));

			vec3 temp;
			if(materialType == FOLIAGE) {
				temp = cookTorrance(lightDirectionView, lightDiffuse,
									attenuation, V, positionView, normalView,
									roughness, 0, diffuseColor, specularColor);
				temp = attenuation * lightDiffuse * diffuseColor * clamp(dot(-normalView, lightDirectionView), 0, 1);
			} else if(materialType == UNLIT) {
				finalColor = color;
			} else {
				temp = cookTorrance(lightDirectionView, lightDiffuse,
									attenuation, V, positionView, normalView,
									roughness, metallic, diffuseColor, specularColor);
			}
			temp = temp * lightDiffuse * attenuation;

			float visibility = 1.0f;


			if(pointLight.shadow != 0) {
				visibility = getVisibilityCubemap(positionWorld, lightIndex, pointLight);
			}

			finalColor.rgb += temp * visibility;
		}
	}
*/

// TODO: Shadow not working properly for some reason
#ifdef BINDLESSTEXTURES
	float visibility = getVisibility(positionWorld, directionalLight);
#else
	float visibility = getVisibility(positionWorld.xyz, directionalLight, directionalLightShadowMap, directionalLightStaticShadowMap);
#endif
visibility = 1;

	vec3 finalColorDirectional;
	vec3 lightDirectionView = (viewMatrix * vec4(-directionalLight.direction, 0)).xyz;
	if(materialType == FOLIAGE) {
		finalColorDirectional = cookTorrance(lightDirectionView, directionalLight.color,
								  1, V, positionView, normalView,
								  roughness, 0, diffuseColor, specularColor);
		finalColorDirectional *= visibility;
	} else if(materialType == UNLIT) {
		finalColorDirectional = color;
	} else {
		finalColorDirectional = clamp(cookTorrance(lightDirectionView, directionalLight.color, 1.0f, V, positionView, normalView, roughness, metallic, diffuseColor, specularColor), 0, 1);
		finalColorDirectional *= visibility;
	}

	finalColor += finalColorDirectional;
	finalColor += 0.1 * diffuseColor.rgb;

	imageStore(out_color, storePos, vec4(finalColor, 1));
}
