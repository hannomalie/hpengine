
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;

layout(binding=8) uniform samplerCubeArray probes;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;

uniform vec3 eyePosition;
uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform float scatterFactor = 1;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_DiffuseSpecular;
layout(location=1)out vec4 out_AOReflection;

//include(globals.glsl)

///////////////////// AO
uniform bool useAmbientOcclusion = false;
uniform bool useColorBleeding = false;
uniform float ambientOcclusionRadius = 0.006;
uniform float ambientOcclusionTotalStrength = 0.38;
uniform float ambientOcclusionStrength = 0.7;
uniform float ambientOcclusionFalloff = 0.001;


float maxDepth(sampler2D sampler, vec2 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0025);
	
	float result = texture(sampler, texCoords + vec2(-blurDistance, -blurDistance)).r;
	result = max(result, texture(sampler, texCoords + vec2(0, -blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, -blurDistance)).r);
	
	result = max(result, texture(sampler, texCoords + vec2(-blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(0, 0)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, 0)).r);
	
	result = max(result, texture(sampler, texCoords + vec2(-blurDistance, blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(0, -blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, blurDistance)).r);
	
	return result;
}

float linstep(float low, float high, float v){
    return clamp((v-low)/(high-low), 0.0, 1.0);
}

float radicalInverse_VdC(uint bits) {
     bits = (bits << 16u) | (bits >> 16u);
     bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
     bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
     bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
     bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
     
     return float(bits) * 2.3283064365386963e-10; // / 0x100000000
}
vec2 hammersley2d(uint i, int N) {
	return vec2(float(i)/float(N), radicalInverse_VdC(i));
}

vec3 PCF(sampler2D sampler, vec2 texCoords, float referenceDepth, float inBlurDistance) {
	vec3 result = vec3(0,0,0);
	float blurDistance = clamp(inBlurDistance, 0.0, 0.002);
	const int N = 32;
	const float bias = 0.001;
	for (int i = 0; i < N; i++) {
		result += (texture(sampler, texCoords + (hammersley2d(i, N)-0.5)/100).x > referenceDepth - bias ? 1 : 0);
	}
	return result/N;
}
vec3 blurESM(sampler2D sampler, vec2 texCoords, float dist, float inBlurDistance) {
	float darknessFactor = 120.0;
	vec3 result = vec3(0,0,0);
	float blurDistance = clamp(inBlurDistance, 0.0, 0.002);
	const int N = 32;
	const float bias = 0.001;
	for (int i = 0; i < N; i++) {
		float moment = texture(sampler, texCoords + (hammersley2d(i, N)-0.5)/100).x;
		result += clamp(exp(darknessFactor * (moment - dist)), 0.0, 1.0);
	}
	return result/N;
}

vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
  		float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
		return vec3(0,0,0);
	}
	if(USE_PCF) {
		return PCF(shadowMap, ShadowCoordPostW.xy, dist, 0.002);
	}
	
	
	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 2);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;
	
	const bool AVOID_LIGHT_BLEEDING = false;
	if(AVOID_LIGHT_BLEEDING) {
		float envelopeMaxDepth = maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0025);
		envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0017);
		envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.00125);
		envelopeMaxDepth /= 3;
		if(envelopeMaxDepth < dist - 0.005) { return vec3(0,0,0); }
	}
	
	moments = blur(shadowMap, ShadowCoordPostW.xy, 0.0125, 1).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.0017).rg;
	//moments += blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;
	//moments /= 3;
	
	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist <= moments.x) {
		//return vec3(1.0,1.0,1.0);
	}
	
	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.0005);

	float d = dist - moments.x;
	//float p_max = (variance / (variance + d*d));
	// thanks, for light bleeding reduction, FOOGYWOO! http://dontnormalize.me/ 
	float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));
	
	p_max = smoothstep(0.1, 1.0, p_max);

	float darknessFactor = 420.0;
	p_max = clamp(exp(darknessFactor * (moments.x - dist)), 0.0, 1.0);
	
	//p_max = blurESM(shadowMap, ShadowCoordPostW.xy, dist, 0.002);

	return vec3(p_max,p_max,p_max);
}

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

/////////////////////

float ComputeScattering(float lightDotView)
{
	const float G_SCATTERING = 0.000005;
	const float PI = 3.1415926536f;
	float result = 1.0f - G_SCATTERING;
	result *= result;
	result /= (4.0f * PI * pow(1.0f + G_SCATTERING * G_SCATTERING - (2.0f * G_SCATTERING) * lightDotView, 1.5f));
	return result;
}

bool isInside(vec3 position, vec3 minPosition, vec3 maxPosition) {
	return(all(greaterThanEqual(position, minPosition)) && all(lessThanEqual(position, maxPosition))); 
}


float[16] ditherPattern = { 0.0f, 0.5f, 0.125f, 0.625f,
							0.75f, 0.22f, 0.875f, 0.375f,
							0.1875f, 0.6875f, 0.0625f, 0.5625,
							0.9375f, 0.4375f, 0.8125f, 0.3125};

vec3 scatter(vec3 worldPos, vec3 startPosition) {
	const int NB_STEPS = 40;
	 
	vec3 rayVector = worldPos.xyz - startPosition;
	 
	float rayLength = length(rayVector);
	vec3 rayDirection = rayVector / rayLength;
	 
	float stepLength = rayLength / NB_STEPS;
	 
	vec3 step = rayDirection * stepLength;
	 
	vec3 currentPosition = startPosition;
	 
	vec3 accumFog = vec3(0,0,0);
	 
	for (int i = 0; i < NB_STEPS; i++)
	{
		vec4 shadowPos = shadowMatrix * vec4(currentPosition, 1.0f);
		vec4 worldInShadowCameraSpace = shadowPos;
		worldInShadowCameraSpace /= worldInShadowCameraSpace.w;
    	vec2 shadowmapTexCoord = (worldInShadowCameraSpace.xy * 0.5 + 0.5);
    	
    	float ditherValue = ditherPattern[int(gl_FragCoord.x) % 4 + int(gl_FragCoord.y) % 4];
    	
	  	/*if (shadowmapTexCoord.x < 0 || shadowmapTexCoord.x > 1 || shadowmapTexCoord.y < 0 || shadowmapTexCoord.y > 1) {
			continue;
		}*/
		float shadowMapValue = textureLod(shadowMap, shadowmapTexCoord,0).r;
		 
		if (shadowMapValue > (worldInShadowCameraSpace.z - ditherValue * 0.0001))
		{
			accumFog += ComputeScattering(dot(rayDirection, lightDirection));
		} else {
			vec3 probeColor;// = textureLod(probes, vec4(0,-1, 0, 0), 10).rgb;
			
			for(int z = 0; z < activeProbeCount; z++) {
				vec3 currentEnvironmentMapMin = environmentMapMin[z];
				vec3 currentEnvironmentMapMax = environmentMapMax[z];
				vec3 halfExtents = (currentEnvironmentMapMax - currentEnvironmentMapMin)/2;
				vec3 probeCenter = currentEnvironmentMapMin + halfExtents;
				if(isInside(currentPosition, currentEnvironmentMapMin, currentEnvironmentMapMax)) {
					float mipmap = 2;
					probeColor = textureLod(probes, vec4(vec3(0,1,0), z), mipmap).rgb/6;
					probeColor +=textureLod(probes, vec4(vec3(0,-1,0), z), mipmap).rgb/6;
					probeColor +=textureLod(probes, vec4(vec3(1,0,0), z), mipmap).rgb/6;
					probeColor +=textureLod(probes, vec4(vec3(-1,0,0), z), mipmap).rgb/6;
					probeColor +=textureLod(probes, vec4(vec3(0,0,1), z), mipmap).rgb/6;
					probeColor +=textureLod(probes, vec4(vec3(0,0,-1), z), mipmap).rgb/6;
					accumFog += ComputeScattering(dot(rayDirection, rayVector)) *1*probeColor;
					break;
				}
			}
		}

		currentPosition += step;
	}
	accumFog /= NB_STEPS;
	return accumFog * lightDiffuse;
}

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	st /= secondPassScale;
  	
	float depth = texture2D(normalMap, st).w;
	vec3 positionView = texture2D(positionMap, st).xyz;
	//vec4 positionViewPreW = (inverse(projectionMatrix)*vec4(st, depth, 1));
	//positionView = positionViewPreW.xyz / positionViewPreW.w;
  	
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 color = texture2D(diffuseMap, st).xyz;
	float roughness = texture2D(positionMap, st).a;
	
  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	V = positionView;

	// skip background
	if (positionView.z > -0.0001) {
	  discard;
	}
	vec4 normalAmbient = texture2D(normalMap, st);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView,0.0)).xyz;
	
	float metallic = texture2D(diffuseMap, st).a;
	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, roughness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

	vec3 lightDirectionView = (viewMatrix * vec4(lightDirection, 0)).xyz;
	vec3 finalColor = cookTorrance(lightDirectionView, lightDiffuse, 1.0f, V, positionView, normalView, roughness, metallic, diffuseColor, specularColor);
	
	/////////////////// SHADOWMAP
	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow), 0, 1).r;
	///////////////////


	int materialIndex = int(textureLod(visibilityMap, st, 0).b);
	Material material = materials[materialIndex];
	if(int(material.materialtype) == 1) {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse,
									1, V, positionView, normalView,
									roughness, 0, diffuseColor, specularColor);
		finalColor += diffuseColor * lightDiffuse * clamp(dot(-normalView, lightDirectionView), 0, 1);
	}
	
	finalColor *= visibility;

	out_DiffuseSpecular.rgb = 4 * finalColor;
	
	float ambient = normalAmbient.a;
	ambient += 0.001;
	out_DiffuseSpecular.rgb += ambient * color.rgb;
	
	//out_DiffuseSpecular.rgb = normalWorld/2+1;
	//out_AOReflection.gba = vec3(0,0,0);
	/*if(SCATTERING) {
		out_AOReflection.gba += scatterFactor * scatter(positionWorld, -eyePosition);
	} else {
		out_AOReflection.gba = vec3(0,0,0);
	}*/
	
	//out_DiffuseSpecular = vec4(color,1);
	//out_DiffuseSpecular.rgb = vec3(depthInLightSpace,depthInLightSpace,depthInLightSpace);
	//out_DiffuseSpecular.rgb = vec3(positionShadow.xyz);
}
