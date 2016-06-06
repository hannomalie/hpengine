
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal

layout(binding=8) uniform samplerCubeArray probes;
layout(binding=13) uniform sampler3D grid;

uniform float screenWidth = 1280/2;
uniform float screenHeight = 720/2;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;

uniform vec3 eyePosition;
uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform float scatterFactor = 1;

uniform bool useAmbientOcclusion = true;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

uniform float sceneScale = 1;
uniform float inverseSceneScale = 1;
uniform int gridSize;

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_AOScattering;

float packColor(vec3 color) {
    return color.r + color.g * 256.0 + color.b * 256.0 * 256.0;
}
vec3 unpackColor(float f) {
    vec3 color;
    color.b = floor(f / 256.0 / 256.0);
    color.g = floor((f - color.b * 256.0 * 256.0) / 256.0);
    color.r = floor(f - color.b * 256.0 * 256.0 - color.g * 256.0);
    // now we have a vec3 with the 3 components in range [0..256]. Let's normalize it!
    return color / 256.0;
}

// http://aras-p.info/texts/CompactNormalStorage.html
#define kPI 3.1415926536f
vec3 decode(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 positionView, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-positionView);
	//V = ViewVector;
 	vec3 L = -normalize((vec4(lightDirection, 0)).xyz);
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
	
	vec3 diff = diffuseColor * lightDiffuse.rgb * NdotL;
	
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
	
	return diff + cookTorrance * lightDiffuse.rgb * specularColor;
}

///////////////////// AO
uniform float ambientOcclusionRadius = 0.006;
uniform float ambientOcclusionTotalStrength = 0.38;
uniform float ambientOcclusionStrength = 0.7;
uniform float ambientOcclusionFalloff = 0.001;

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
	return 0.5+(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453))*0.5;
}

const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0 };
				

vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0125);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * texture(sampler, texCoords + vec2(-blurDistance, -blurDistance));
	result += kernel[1] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[2] * texture(sampler, texCoords + vec2(blurDistance, -blurDistance));
	
	result += kernel[3] * texture(sampler, texCoords + vec2(-blurDistance));
	result += kernel[4] * texture(sampler, texCoords + vec2(0, 0));
	result += kernel[5] * texture(sampler, texCoords + vec2(blurDistance, 0));
	
	result += kernel[6] * texture(sampler, texCoords + vec2(-blurDistance, blurDistance));
	result += kernel[7] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[8] * texture(sampler, texCoords + vec2(blurDistance, blurDistance));
	
	return result;
}

vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance, float mipmap) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0025);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * textureLod(sampler, texCoords + vec2(-blurDistance, -blurDistance), mipmap);
	result += kernel[1] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipmap);
	result += kernel[2] * textureLod(sampler, texCoords + vec2(blurDistance, -blurDistance), mipmap);
	
	result += kernel[3] * textureLod(sampler, texCoords + vec2(-blurDistance), mipmap);
	result += kernel[4] * textureLod(sampler, texCoords + vec2(0, 0), mipmap);
	result += kernel[5] * textureLod(sampler, texCoords + vec2(blurDistance, 0), mipmap);
	
	result += kernel[6] * textureLod(sampler, texCoords + vec2(-blurDistance, blurDistance), mipmap);
	result += kernel[7] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipmap);
	result += kernel[8] * textureLod(sampler, texCoords + vec2(blurDistance, blurDistance), mipmap);
	
	return result;
}

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

vec4 voxelFetch(vec3 positionWorld, float loD) {
    const int gridSizeHalf = gridSize/2;

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    if(any(greaterThan(positionGridScaled, vec3(gridSizeHalf))) ||
       any(lessThan(positionGridScaled, -vec3(gridSizeHalf)))) {

       return vec4(0);
    }

    int level = int(loD);
    vec3 positionAdjust = vec3(gridSize/pow(2, level+1));
    float positionScaleFactor = pow(2, level);

    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(gridSize)+vec3(0.5);
    return textureLod(grid, samplePositionNormalized, level);
}

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

float dotSaturate(vec3 a, vec3 b) {
	return clamp(dot(a, b),0,1);
}

vec3 scatter(vec3 worldPos, vec3 startPosition) {
	const int NB_STEPS = 40;
	 
	vec3 rayVector = worldPos.xyz - startPosition;
	 
	float rayLength = length(rayVector);
	vec3 rayDirection = rayVector / rayLength;
	 
	float stepLength = rayLength / NB_STEPS;
	 
	vec3 step = rayDirection * stepLength;
	 
	vec3 currentPosition = startPosition;
	 
	vec3 accumFog = vec3(0,0,0);
	vec3 accumFogShadow = vec3(0,0,0);
	 
	for (int i = 0; i < NB_STEPS; i++)
	{
		vec4 shadowPos = shadowMatrix * vec4(currentPosition, 1.0f);
		vec4 worldInShadowCameraSpace = shadowPos;
		worldInShadowCameraSpace /= worldInShadowCameraSpace.w;
    	vec2 shadowmapTexCoord = (worldInShadowCameraSpace.xy * 0.5 + 0.5);
    	
    	float ditherValue = ditherPattern[int(gl_FragCoord.x) % 4 + int(gl_FragCoord.y) % 4];
    	
		float shadowMapValue = textureLod(shadowMap, shadowmapTexCoord,0).r;

		 float NdotL = clamp(dot(rayDirection, lightDirection), 0, 1);
		if (shadowMapValue > (worldInShadowCameraSpace.z - ditherValue * 0.0001))
		{
			accumFog += ComputeScattering(NdotL);
		}
		{
//			accumFogShadow += 0.0005f * ComputeScattering(NdotL);

			const bool useVoxelConeTracing = false;
			if(useVoxelConeTracing) {
			    int mipLevel = 5;
				vec4 voxel = voxelFetch(currentPosition, mipLevel);
				accumFogShadow += voxel.rgb;
			}
		}

		currentPosition += step;
	}
	accumFog /= NB_STEPS;
	return (accumFog * lightDiffuse) + accumFogShadow;
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

    const bool useCrytekAO = false;
    if(useCrytekAO) {
        const vec2 vec[4] = {vec2(1,0),vec2(-1,0),
                    vec2(0,1),vec2(0,-1)};
        vec3 p = getPosition(st);
        vec3 n = getNormal(st);
        vec2 rand = vec2(rand(st), rand(vec2(1)-st));
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

        return 1-ao;

    } else {
        float ao = 1;
        vec3 ssdo = vec3(0,0,0);

        float sum = 0.0;
        float prof = texture(motionMap, st.xy).b; // depth
        vec3 norm = normalize(vec3(texture(normalMap, st.xy).xyz)); //*2.0-vec3(1.0)
        const int NUM_SAMPLES = 4;
        int hf = NUM_SAMPLES/2;

        //calculate sampling rates:
        float ratex = (1.0/screenWidth);
        float ratey = (1.0/screenHeight);
        float incx2 = ratex*8;//ao radius
        float incy2 = ratey*8;
        if (useAmbientOcclusion) {
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
                          sum += clamp(pformfactor2*0.25,0.0,1.0);//ao intensity;
                      }
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

	float depth = texture2D(normalMap, st).w;
	vec3 positionView = texture2D(positionMap, st).xyz;
  	
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;

  	if(useAmbientOcclusion) {
		out_AOScattering.r = getAmbientOcclusion(st);
  	} else {
  		out_AOScattering.r = 1;
  	}
  	if(SCATTERING) {
  		out_AOScattering.gba += scatterFactor * scatter(positionWorld, eyePosition);
  	} else {
  		out_AOScattering.gba = vec3(0,0,0);
  	}
}
