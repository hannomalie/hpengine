
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal

layout(binding=8) uniform samplerCubeArray pointLightShadowMapsCube;
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

uniform bool useAmbientOcclusion = true;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

uniform int useVoxelGrid = 0;

uniform int maxPointLightShadowmaps;
uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};
layout(std430, binding=3) buffer _directionalLightState {
	DirectionalLightState directionalLight;
};
layout(std430, binding=5) buffer _voxelGrids {
    VoxelGridArray voxelGridArray;
};
in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_AOScattering;


bool isInsideSphere(vec3 positionToTest, vec3 positionSphere, float radius) {
	return distance(positionSphere, positionToTest) < radius;
}

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

vec3 cookTorrance(in vec3 ViewVector, in vec3 positionView, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-positionView);
	//V = ViewVector;
 	vec3 L = -normalize((vec4(directionalLight.direction, 0)).xyz);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);
	
	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	//roughness = (roughness+1)/2;
	float alpha = roughness*roughness;
	float alphaSquare = alpha*alpha;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float denom = (NdotH*NdotH*(alphaSquare-1))+1;
	float D = alphaSquare/(3.1416*denom*denom);
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
    
    // Schlick
    float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2, electrics up to 1 and mostly above 0.5
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(L, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	vec3 diff = diffuseColor * directionalLight.color.rgb * NdotL;
	
	/////////////////////////
	// OREN-NAYAR
	{
		float angleVN = acos(NdotV);
	    float angleLN = acos(NdotL);
	    float alpha = max(angleVN, angleLN);
	    float beta = min(angleVN, angleLN);
	    float gamma = dot(V - N * dot(V, L), L - N * NdotL);
	    float roughnessSquared = alpha;
	    float A = 1.0 - 0.5 * (roughnessSquared / (roughnessSquared + 0.57));
	    float B = 0.45 * (roughnessSquared / (roughnessSquared + 0.09));
	    float C = sin(alpha) * tan(beta);
	    diff *= (A + B * max(0.0, gamma) * C);
    }
	/////////////////////////
	
	//diff = diff * (1-fresnel); // enegy conservation between diffuse and spec http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	
	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	
	return diff + cookTorrance * directionalLight.color.rgb * specularColor;
}

///////////////////// AO
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
vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
  		float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
		return vec3(0,0,0);
	}
	//return PCF(shadowMap, ShadowCoordPostW.xy, dist, 0.002);
	
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
	
	moments = blur(shadowMap, ShadowCoordPostW.xy, 0.00125, 1).rg;
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
	
	p_max = smoothstep(0.2, 1.0, p_max);

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

float[16] ditherPattern = { 0.0f, 0.5f, 0.125f, 0.625f,
							0.75f, 0.22f, 0.875f, 0.375f,
							0.1875f, 0.6875f, 0.0625f, 0.5625,
							0.9375f, 0.4375f, 0.8125f, 0.3125};

float dotSaturate(vec3 a, vec3 b) {
	return clamp(dot(a, b),0,1);
}

vec3 scatter(vec3 worldPos, vec3 startPosition) {
	const int NB_STEPS = 40;
	 
	vec3 rayVector = worldPos.xyz - startPosition;
	 
	float rayLength = length(rayVector);
	vec3 rayDirection = rayVector / vec3(rayLength);
	 
	float stepLength = rayLength / NB_STEPS;
	 
	vec3 step = rayDirection * stepLength;
	 
	vec3 currentPosition = startPosition;
	 
	vec3 accumFog = vec3(0);
	vec3 accumFogShadow = vec3(0);
	 
	for (int i = 0; i < NB_STEPS; i++)
	{
		vec4 shadowPos = directionalLight.viewProjectionMatrix * vec4(currentPosition, 1.0f);
		vec4 worldInShadowCameraSpace = shadowPos;
		worldInShadowCameraSpace /= worldInShadowCameraSpace.w;
    	vec2 shadowmapTexCoord = (worldInShadowCameraSpace.xy * 0.5 + 0.5);
    	
    	float ditherValue = ditherPattern[int(gl_FragCoord.x) % 4 + int(gl_FragCoord.y) % 4];
    	
		float shadowMapValue = textureLod(shadowMap, shadowmapTexCoord, 0).r;

		bool notInShadow = shadowMapValue > (worldInShadowCameraSpace.z - ditherValue * 0.0001);
		if (notInShadow) {
			float NdotL = clamp(dot(normalize(rayDirection), normalize(directionalLight.direction)), 0.0, 1.0);
			accumFog += vec3(0.25f*directionalLight.scatterFactor) * vec3(ComputeScattering(NdotL)) * directionalLight.color;
		}
		for(int lightIndex = 0; lightIndex < pointLightCount; lightIndex++) {
			PointLight pointLight = pointLights[lightIndex];

			vec3 pointLightPosition = pointLight.position;
			if(isInsideSphere(currentPosition, pointLightPosition, float(pointLight.radius))){
				float visibilityPointLight = getVisibilityCubemap(currentPosition, lightIndex, pointLight);
				float attenuationPointLight = distance(currentPosition, pointLightPosition)/float(pointLight.radius);
				accumFog += pointLight.color * vec3(ComputeScattering(attenuationPointLight)) * visibilityPointLight;
			}
		}
		if(useVoxelGrid == 1) {
			float mipLevel = 2.5f;
			vec3 randomPosition = currentPosition/30.0f;
			float rand = surface3(randomPosition + vec3(0.0003f)*vec3(time%1000000), 0.5f);
			for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
				VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];
#ifdef BINDLESS_TEXTURES
				sampler3D gridSampler = toSampler(voxelGrid.gridHandle);
				vec4 voxel = voxelFetch(voxelGrid, gridSampler, currentPosition, mipLevel);
#else
				vec4 voxel = voxelFetch(voxelGrid, grid, currentPosition, mipLevel);
#endif
				accumFogShadow += 3.5*rand*voxel.rgb;
			}

			const float maxFogHeight = 5;
			const bool useGroundFog = false;
			if(useGroundFog) {
				float z = 1-clamp(distance(currentPosition.y, 0)/maxFogHeight, 0, 1);
				accumFogShadow += 0.02*(vec3(1)-vec3(rand))*mix(0, 1, z);
			}
		}

		currentPosition += step;
	}
	accumFog /= NB_STEPS;
	return clamp(accumFog + accumFogShadow, vec3(0.0f), vec3(10.0f));
}

vec3 getPosition(in vec2 uv) {
    return textureLod(positionMap, uv, 0).xyz;
}
vec3 getNormal(in vec2 uv) {
    return textureLod(normalMap, uv, 0).xyz;
}

float doAmbientOcclusion(in vec2 tcoord,in vec2 uv, in vec3 p, in vec3 cnorm) {
    float g_bias = 0.5f;
    float g_scale = 0.01;
    float g_intensity = 3.5;

    vec3 diff = getPosition(tcoord + uv) - p;
    const vec3 v = normalize(diff);
    const float d = length(diff)*g_scale;
    return max(0.0,dot(cnorm,v)-g_bias)*(1.0/(1.0+d))*g_intensity;
}
float getAmbientOcclusion(vec2 st) {

    const bool useCrytekAO = true;
    if(useCrytekAO) {
        const vec2 vec[4] = {vec2(1,0),vec2(-1,0),
                    vec2(0,1),vec2(0,-1)};
        vec3 p = getPosition(st);
        vec3 n = getNormal(st);
        vec2 rand = vec2(snoise(st), snoise(1-st));//vec2(rand(st), rand(vec2(1)-st));
        float g_sample_rad = 10;

        float ao = 0.0f;
        float rad = g_sample_rad/p.z;

        //**SSAO Calculation**//
        int iterations = 4;
        for (int j = 0; j < iterations; ++j) {
          vec2 coord1 = reflect(vec[j],rand)*rad;
          vec2 coord2 = vec2(coord1.x*0.707 - coord1.y*0.707,
                      coord1.x*0.707 + coord1.y*0.707);

          ao += doAmbientOcclusion(st,coord1*0.25, p, n);
          ao += doAmbientOcclusion(st,coord2*0.5, p, n);
          ao += doAmbientOcclusion(st,coord1*0.75, p, n);
          ao += doAmbientOcclusion(st,coord2, p, n);
        }
        ao/= float(iterations)*4.0;

        return 1.0*(1-ao);

    } else {
        float ao = 1;
        vec3 ssdo = vec3(0,0,0);

        float sum = 0.0;
        float prof = texture(motionMap, st.xy).b; // depth
        vec3 norm = normalize(vec3(texture(normalMap, st.xy).xyz)); //*2.0-vec3(1.0)
        const int NUM_SAMPLES = 14;
        int hf = NUM_SAMPLES/2;

        //calculate sampling rates:
        float ratex = (1.0f/screenWidth);
        float ratey = (1.0f/screenHeight);
        float incx2 = ratex*8.0f;//ao radius
        float incy2 = ratey*8.0f;
		for(int i=-hf; i < hf; i++) {
			  for(int j=-hf; j < hf; j++) {

			  if (i != 0 || j!= 0) {

				  vec2 coords2 = vec2(i*incx2,j*incy2)/prof;

				  float prof2g = texture2D(motionMap,st.xy+coords2*rand(st.xy)).b; // depth
				  vec3 norm2g = normalize(vec3(texture2D(normalMap,st.xy+coords2*rand(st.xy)).xyz)); //*2.0-vec3(1.0)

				  //OCCLUSION:
				  //calculate approximate pixel distance:
				  vec3 dist2 = vec3(coords2,prof-prof2g);
				  //calculate normal and sampling direction coherence:
				  float coherence2 = dot(normalize(-coords2),normalize(vec2(norm2g.xy)));
				  //if there is coherence, calculate occlusion:
				  if (coherence2 > 0){
					  float pformfactor2 = 0.5*((1.0-dot(norm,norm2g)))/(3.1416*pow(abs(length(dist2*2)),2.0)+0.5);//el 4: depthscale
					  sum += clamp(pformfactor2*0.25,0.0,.075);//ao intensity;
				  }
			  }
		   }
		}

        ao = clamp(1.0-(sum/NUM_SAMPLES),0,1);
        return ao;
    }
}
void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	float depth = textureLod(normalMap, st, 0).w;
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
  	
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;

	vec4 result = vec4(0,0,0,0);
  	if(useAmbientOcclusion) {
		result.r = getAmbientOcclusion(st);
  	}
  	if(SCATTERING) {
  		result.gba = 10*directionalLight.scatterFactor * scatter(positionWorld, eyePosition);
  	}
	out_AOScattering = result;
}
