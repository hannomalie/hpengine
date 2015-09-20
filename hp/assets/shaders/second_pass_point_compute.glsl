#define WORK_GROUP_SIZE 16

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4, rgba16f) uniform image2D out_DiffuseSpecular;
layout(binding=5) uniform sampler2D probe;
layout(binding=7) uniform sampler2D visibilityMap;


uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _lights {
	float pointLightCount;
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

float calculateAttenuation(float dist, float lightRadius) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
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

shared uint currentPointLightIndex = 0;
shared uint currentArrayIndex = 0;
shared uint[10] pointLightIndicesForTile;

shared uint minDepth = 0xFFFFFFFF;
shared uint maxDepth = 0;

bool isInsideSphere(vec3 positionToTest, vec3 positionSphere, float radius) {

	return all(distance(positionSphere, positionToTest) < radius);

}

void main(void) {
	
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	vec2 st = vec2(storePos) / vec2(screenWidth, screenHeight);
  
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;

	vec3 color = texture2D(diffuseMap, st).xyz;
	float roughness = texture2D(positionMap, st).w;
	float metallic = texture2D(diffuseMap, st).w;

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
	vec4 specular = texture2D(specularMap, st);
	float depthFloat = texture2D(normalMap, st).w;
	depthFloat = textureLod(visibilityMap, st, 0).g;
	uint depth = uint(depthFloat * 0xFFFFFFFF);


	atomicMin(minDepth, depth);
	atomicMax(maxDepth, depth);
	barrier();
	float minDepthZ = float(minDepth / float(0xFFFFFFFF));
    float maxDepthZ = float(maxDepth / float(0xFFFFFFFF));

	vec2 tileScale = vec2(screenWidth, screenHeight) * (1.0f / float(2*WORK_GROUP_SIZE));
	vec2 tileBias = tileScale - vec2(gl_WorkGroupID.xy);
	vec4 c1 = vec4(-projectionMatrix[0][0] * tileScale.x, 0.0f, tileBias.x, 0.0f);
	vec4 c2 = vec4(0.0f, -projectionMatrix[1][1] * tileScale.y, tileBias.y, 0.0f);
	vec4 c4 = vec4(0.0f, 0.0f, 1.0f, 0.0f);
	 // Derive frustum planes
	vec4 frustumPlanes[6];
	// Sides
	//right
	frustumPlanes[0] = c4 - c1;
	//left
	frustumPlanes[1] = c4 + c1;
	//bottom
	frustumPlanes[2] = c4 - c2;
	//top
	frustumPlanes[3] = c4 + c2;
	// Near/far
	frustumPlanes[4] = vec4(0.0f, 0.0f,  1.0f, -minDepthZ);
	frustumPlanes[5] = vec4(0.0f, 0.0f, -1.0f,  maxDepthZ);
	for(int i = 0; i < 4; i++)
	{
		frustumPlanes[i] *= 1.0f / length(frustumPlanes[i].xyz);
	}

	const vec3 bias = vec3(0);
	uint threadCount = WORK_GROUP_SIZE * WORK_GROUP_SIZE;
	uint passCount = (uint(pointLightCount) + threadCount - 1) / threadCount;
    for (uint passIt = 0; passIt < passCount; ++passIt)
    {
        uint lightIndex =  passIt * threadCount + gl_LocalInvocationIndex;
		PointLight pointLight = pointLights[lightIndex];
		vec3 pointLightPosition = vec3(pointLight.positionX, pointLight.positionY, pointLight.positionZ);
		vec4 pos = (viewMatrix * vec4(pointLightPosition, 1.0f));
		float rad = 2*pointLight.radius;

		const int MAX_LIGHTS_PER_TILE = 256;
		if (lightIndex < uint(pointLightCount) && lightIndex < MAX_LIGHTS_PER_TILE)
		{
			bool inFrustum = true;
			for (uint i = 3; i >= 0 && inFrustum; i--)
			{
				float dist = dot(frustumPlanes[i], pos);
				inFrustum = (-rad <= dist);
			}

			if (inFrustum)
			{
				uint id = atomicAdd(currentArrayIndex, 1);
				pointLightIndicesForTile[id] = lightIndex;
			}
		}
	}

	barrier();
	vec3 finalColor = vec3(0,0,0);
	for(int i = 0; i < currentArrayIndex; i++) {
		PointLight pointLight = pointLights[pointLightIndicesForTile[i]];
//	for(int i = 0; i < uint(pointLightCount); i++) {
//		PointLight pointLight = pointLights[i];
		vec3 lightPositionView = (viewMatrix * vec4(pointLight.positionX, pointLight.positionY, pointLight.positionZ, 1)).xyz;
		vec3 lightDiffuse = vec3(pointLight.colorR, pointLight.colorG, pointLight.colorB);
		vec3 lightDirectionView = normalize(vec4(lightPositionView - positionView, 0)).xyz;
		float attenuation = calculateAttenuation(length(lightPositionView - positionView), pointLight.radius);

		int materialIndex = int(textureLod(visibilityMap, st, 0).b);
		Material material = materials[materialIndex];

		if(int(material.materialtype) == 1) {
			finalColor += cookTorrance(lightDirectionView, lightDiffuse,
											attenuation, V, positionView, normalView,
											roughness, 0, diffuseColor, specularColor);
			finalColor += attenuation * lightDiffuse * diffuseColor * clamp(dot(-normalView, lightDirectionView), 0, 1);
		} else
		{
			finalColor += cookTorrance(lightDirectionView, lightDiffuse,
											attenuation, V, positionView, normalView,
											roughness, metallic, diffuseColor, specularColor);
		}
	}
	barrier();
//	if (gl_LocalInvocationID.x == 0 || gl_LocalInvocationID.y == 0 ||
//		gl_LocalInvocationID.x == 16 || gl_LocalInvocationID.y == 16) {
//		imageStore(out_DiffuseSpecular, storePos, vec4(.2f, .2f, .2f, 1.0f));
//	} else
	{
		vec4 oldSample = imageLoad(out_DiffuseSpecular, storePos).rgba;
		imageStore(out_DiffuseSpecular, storePos, vec4(0.5*oldSample.rgb + 0.5*vec3(4 * finalColor.rgb), 0));
	}
//	imageStore(out_DiffuseSpecular, storePos, vec4(st,0,0));
}
