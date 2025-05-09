#define WORK_GROUP_SIZE 16

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

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};

//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

#define kPI 3.1415926536f
const float pointLightRadius = 10.0;

vec3 decodeNormal(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

float chiGGX(float v)
{
    return v > 0 ? 1 : 0;
}

float GGX_PartialGeometryTerm(vec3 v, vec3 n, vec3 h, float alpha)
{
    float VoH2 = clamp(dot(v,h), 0, 1);
    float chi = chiGGX( VoH2 / clamp(dot(v,n), 0, 1) );
    VoH2 = VoH2 * VoH2;
    float tan2 = ( 1 - VoH2 ) / VoH2;
    return (chi * 2) / ( 1 + sqrt( 1 + alpha * alpha * tan2 ) );
}

const int MAX_LIGHTS_PER_TILE = 256;
shared uint currentPointLightIndex = 0;
shared uint currentArrayIndex = 0;
shared uint[MAX_LIGHTS_PER_TILE] pointLightIndicesForTile;

shared uint minDepth = 0xFFFFFFFF;
shared uint maxDepth = 0;

float getVisibilityDPSM(vec3 positionWorld, uint pointLightIndex, PointLight pointLight) {

	const bool USE_POINTLIGHT_SHADOWMAPPING = true;
	if(!USE_POINTLIGHT_SHADOWMAPPING) { return 1.0; }

	const float NearPlane = 0.0001;
	float FarPlane = pointLight.radius;

	vec3 pointLightPositionWorld = pointLight.position;
	vec3 positionInPointLightSpace = positionWorld - pointLightPositionWorld;

	float L = length(positionInPointLightSpace.xyz);
	vec3 projectedPosition = positionInPointLightSpace / L;

	float currentDepth = (L - NearPlane) / (FarPlane - NearPlane);
	vec3 projectedPositionTextureSpace =  projectedPosition.xyz * 0.5 + 0.5;

	float depthToCompareWith;
	vec4 moments;

	float bias = 0.001;
	bool isBack = positionInPointLightSpace.z < 0;
	if(isBack){
		vec2 vTexBack;
		vTexBack.x =  ((projectedPosition.x /  (1.0f - projectedPosition.z)) * 0.5f + 0.5f);
		vTexBack.y =  ((projectedPosition.y /  (1.0f - projectedPosition.z)) * 0.5f + 0.5f);
		depthToCompareWith = texture(pointLightShadowMapsBack, vec3(vTexBack, pointLightIndex)).r;
		moments = blur(pointLightShadowMapsBack, vec3(vTexBack, pointLightIndex), 0.005);


//		moments = texture(pointLightShadowMapsFront, vec3(vTexBack, pointLightIndex));
//		moments.xyz -= pointLightPositionWorld;
//		float L = length(moments.xyz);
//		moments /= L;
//		moments.z += 1;
//		moments.x /= moments.z;
//		moments.y /= moments.z;
//		moments.z = (L - NearPlane) / (FarPlane - NearPlane);
//		moments.w = 1;
//		depthToCompareWith = moments.z;

	} else
	{
		vec2 vTexFront;
		vTexFront.x =  ((projectedPosition.x /  (1.0f + projectedPosition.z)) * 0.5f + 0.5f);
		vTexFront.y =  ((projectedPosition.y /  (1.0f + projectedPosition.z)) * 0.5f + 0.5f);
		depthToCompareWith = texture(pointLightShadowMapsFront, vec3(vTexFront, pointLightIndex)).r;
		moments = blur(pointLightShadowMapsFront, vec3(vTexFront, pointLightIndex), 0.025);


//		moments = texture(pointLightShadowMapsFront, vec3(vTexFront, pointLightIndex));
//		moments.xyz -= pointLightPositionWorld;
//		float L = length(moments.xyz);
//		moments /= L;
//		moments.z += 1;
//		moments.x /= moments.z;
//		moments.y /= moments.z;
//		moments.z = (L - NearPlane) / (FarPlane - NearPlane);
//		moments.w = 1;
//		depthToCompareWith = moments.z;
	}

	float litFactor = (depthToCompareWith + bias) < currentDepth ? 0.0 : 1;
	return litFactor;
	const float SHADOW_EPSILON = 0.001;
	float E_x2 = moments.y;
	float Ex_2 = moments.x * moments.x;
	float variance = min(max(E_x2 - Ex_2, 0.0) + SHADOW_EPSILON, 1.0);
	float m_d = (moments.x - currentDepth);
	float p = variance / (variance + m_d * m_d); //Chebychev's inequality

	litFactor = max(litFactor, p + 0.0025f);

	return litFactor;
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
	if(pointLightIndex > maxPointLightShadowmaps) { return 1.0f; }
	vec3 pointLightPositionWorld = pointLight.position;

	vec3 fragToLight = positionWorld - pointLightPositionWorld;
    vec4 textureSample = textureLod(pointLightShadowMapsCube, vec4(fragToLight, pointLightIndex), 0);
    float closestDepth = textureSample.r;
    vec2 moments = textureSample.xy;
//    closestDepth *= 250.0;
    float currentDepth = VectorToDepthValue(fragToLight, pointLight.radius);//length(fragToLight);
    float bias = 0.0002;
    float shadow = currentDepth - bias < closestDepth ? 1.0 : 0.0;

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

			int materialIndex = int(textureLod(visibilityMap, st, 0).b);
			Material material = materials[materialIndex];

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
			temp = temp * lightDiffuse * attenuation;

			float visibility = 1.0f;

			if(pointLight.shadow != 0) {
				visibility = getVisibility(positionWorld, lightIndex, pointLight);
			}

			finalColor.rgb += temp * visibility;
		}
	}

	vec4 oldSample = imageLoad(out_DiffuseSpecular, storePos).rgba;
	imageStore(out_DiffuseSpecular, storePos, vec4(0.5*oldSample.rgb + 0.5*vec3(4 * finalColor.rgb), 0));
//	imageStore(out_DiffuseSpecular, storePos, vec4(finalColor.rgb,0));

//	imageStore(out_DiffuseSpecular, storePos, vec4(st,0,0));
}
