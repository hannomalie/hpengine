#define WORK_GROUP_SIZE 16

//include(globals_structs.glsl)

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4, rgba16f) uniform image2D out_DiffuseSpecular;
layout(binding=5) uniform sampler2D visibilityMap;
layout(binding=6) uniform sampler2DArray pointLightShadowMapsFront;
layout(binding=7) uniform sampler2DArray pointLightShadowMapsBack;
layout(binding=8) uniform samplerCubeArray pointLightShadowMapsCube;


uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform int maxPointLightShadowmaps = 2;


layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};

uniform int nodeCount = 1;
layout(std430, binding=3) buffer _bvh {
	BvhNode nodes[1000];
};

//include(globals.glsl)


float getVisibilityCubemap(vec3 positionWorld, uint pointLightIndex, PointLight pointLight) {
	if(pointLightIndex > maxPointLightShadowmaps) { return 1.0f; }
	vec3 pointLightPositionWorld = pointLight.position;

	vec3 fragToLight = positionWorld - pointLightPositionWorld;
	vec4 textureSample = textureLod(pointLightShadowMapsCube, vec4(fragToLight, pointLightIndex), 0);
	float closestDepth = textureSample.r;
	vec2 moments = textureSample.xy;
	//    closestDepth *= 250.0;
	float currentDepth = length(fragToLight);
	float bias = 0.2;
	float shadow = (currentDepth - bias) < closestDepth ? 1.0 : 0.0;

	//	const float SHADOW_EPSILON = 0.001;
	//	float E_x2 = moments.y;
	//	float Ex_2 = moments.x * moments.x;
	//	float variance = min(max(E_x2 - Ex_2, 0.0) + SHADOW_EPSILON, 1.0);
	//	float m_d = (moments.x - currentDepth);
	//	float p = variance / (variance + m_d * m_d); //Chebychev's inequality
	//	shadow = max(shadow, p + 0.05f);

	return shadow;
}
float getVisibility(vec3 positionWorld, uint pointLightIndex, PointLight pointLight) {
	return getVisibilityCubemap(positionWorld, pointLightIndex, pointLight);
}

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
	vec4 specular = textureLod(specularMap, st, 0);
	float depthFloat = textureLod(normalMap, st, 0).w;
	depthFloat = textureLod(visibilityMap, st, 0).g;

	vec4 finalColor = vec4(0);
	int nextIndex = 0;

	uint maxIterations = 350;
	while(nextIndex < nodeCount && maxIterations > 0) {
		BvhNode node = nodes[nextIndex];
		int hitPointer = nextIndex += 1;
		bool isLeaf = node.missPointer == hitPointer;
		if(isInsideSphere(positionWorld.xyz, node.positionRadius)) {
			if(isLeaf) {
				PointLight light = pointLights[node.lightIndex];
				float attenuation = calculateAttenuation(distance(positionWorld, light.position), light.radius);

				int materialIndex = int(textureLod(visibilityMap, st, 0).b);
				Material material = materials[materialIndex];
				vec3 lightPositionView = (viewMatrix * vec4(light.position, 1)).xyz;
				vec3 lightDiffuse = light.color;
				vec3 lightDirectionView = normalize(vec4(lightPositionView - positionView, 0)).xyz;

				vec3 temp;
				if(int(material.materialtype) == 1) {
					temp = cookTorrance(lightDirectionView, lightDiffuse,
					attenuation, V, positionView, normalView,
					roughness, 0, diffuseColor, specularColor);
					temp = attenuation * lightDiffuse * diffuseColor * clamp(dot(-normalView, lightDirectionView), 0, 1);
				} else {
					temp = cookTorrance(lightDirectionView, lightDiffuse,
					attenuation, V, positionView, normalView,
					roughness, metallic, diffuseColor, specularColor);
				}
				temp = temp * light.color;

				float visibility = getVisibility(positionWorld, node.lightIndex, light);

				finalColor.rgb += temp*visibility;
//				finalColor.rgb += diffuseColor * attenuation * light.color;
			}
			nextIndex = hitPointer;
		} else {
			nextIndex = node.missPointer;
		}

		maxIterations--;
	}

//	if(isInsideSphere(positionWorld.xyz, nodes[0].positionRadius)) {
//		finalColor.rgb = vec3(1.0f,0.0f,0.0f);
//	}


	vec4 oldSample = imageLoad(out_DiffuseSpecular, storePos).rgba;
	imageStore(out_DiffuseSpecular, storePos, vec4(0.5*oldSample.rgb + 0.5 * finalColor.rgb,0));

//	imageStore(out_DiffuseSpecular, storePos, vec4(st,0,0));
}
