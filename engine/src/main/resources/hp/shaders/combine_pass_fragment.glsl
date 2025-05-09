layout(binding=0) uniform sampler2D diffuseMap; // diffuse, metallic
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D reflectionMap; // ao, reflectedColor
layout(binding=3) uniform sampler2D motionMap; // motionVec, probeIndices
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth
layout(binding=6) uniform sampler2D forwardRenderedMap;
layout(binding=7) uniform sampler2D forwardRenderedRevealageMap;
layout(binding=8) uniform sampler2D environment; // reflection
layout(binding=9) uniform sampler2D environmentReflection;
layout(binding=11) uniform sampler2D aoScattering;
layout(binding=13) uniform sampler3D grid;
layout(binding=15) uniform sampler2D indirectHalfScreen;

layout(std430, binding=0) buffer myBlock
{
  float exposure;
};
uniform float worldExposure;

uniform bool AUTO_EXPOSURE_ENABLED = false;

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 projectionMatrix;
uniform vec3 camPosition;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform bool useAmbientOcclusion = true;
uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
//uniform float exposure = 4;

uniform int fullScreenMipmapCount = 10;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[192];
uniform vec3 environmentMapMax[192];

in vec3 position;
in vec2 texCoord;

out vec4 out_color;

vec3 Uncharted2Tonemap(vec3 x)
{
    float A = 0.15;
	float B = 0.50;
	float C = 0.10;
	float D = 0.20;
	float E = 0.02;
	float F = 0.30;

    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
}

vec3 JimToneMap(vec3 x) {
	return (x*(6.2*x+.5))/(x*(6.2*x+1.7)+0.06);
}

const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0
};

const float blurDistance = 0.0025;
const vec2 offsets[9] = { vec2(-blurDistance, -blurDistance),
					vec2(0, -blurDistance),
					vec2(blurDistance, -blurDistance),
					vec2(-blurDistance, 0),
					vec2(0, 0),
					vec2(blurDistance, 0),
					vec2(-blurDistance, blurDistance),
					vec2(0, blurDistance),
					vec2(blurDistance, blurDistance)
};

const float scaleX = 1;
const float scaleY = 0.56;
const vec2 ratio = vec2(scaleX, scaleY);
vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance, float mipLevel) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.025);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * textureLod(sampler, texCoords + vec2(-blurDistance, -blurDistance), mipLevel);
	result += kernel[1] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipLevel);
	result += kernel[2] * textureLod(sampler, texCoords + vec2(blurDistance, -blurDistance), mipLevel);
	
	result += kernel[3] * textureLod(sampler, texCoords + vec2(-blurDistance), mipLevel);
	result += kernel[4] * textureLod(sampler, texCoords + vec2(0, 0), mipLevel);
	result += kernel[5] * textureLod(sampler, texCoords + vec2(blurDistance, 0), mipLevel);
	
	result += kernel[6] * textureLod(sampler, texCoords + vec2(-blurDistance, blurDistance), mipLevel);
	result += kernel[7] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipLevel);
	result += kernel[8] * textureLod(sampler, texCoords + vec2(blurDistance, blurDistance), mipLevel);
	
	return result;
}

vec4 bilateralBlur(sampler2D sampler, vec2 texCoords) {

	vec4 result = vec4(0,0,0,0);
	float normalization = 0;
	
	vec4 centerSample = textureLod(sampler, texCoords + offsets[4], 0);
	float centerSampleDepth = textureLod(normalMap, texCoords + offsets[4], 0).a;
	result += kernel[4] * centerSample;
	
	for(int i = 0; i < 9; i++) {
		if(i == 4) { continue; }
		
		vec4 currentSample = textureLod(sampler, texCoords + offsets[i], 0);
		float currentSampleDepth = textureLod(normalMap, texCoords + offsets[i], 0).a;
		
		float closeness = 1-distance(currentSampleDepth, centerSampleDepth);
		float sampleWeight = kernel[i] * closeness;
		result += sampleWeight * currentSample;
		
		normalization += (1-closeness)*kernel[i]; // this is the amount we have lost.
	}
	
	return result + normalization * centerSample;
}
vec4 bilateralBlurReflection(sampler2D sampler, vec2 texCoords, float roughness) {

	const float blurDistance = 0.0025;
	const vec2 offsets[9] = { vec2(-blurDistance, -blurDistance),
						vec2(0, -blurDistance),
						vec2(blurDistance, -blurDistance),
						vec2(-blurDistance, 0),
						vec2(0, 0),
						vec2(blurDistance, 0),
						vec2(-blurDistance, blurDistance),
						vec2(0, blurDistance),
						vec2(blurDistance, blurDistance)
	};
	vec4 result = vec4(0,0,0,0);
	float normalization = 0;
	
	vec4 centerSample = textureLod(sampler, texCoords + offsets[4], 0);
	float centerSampleDepth = textureLod(normalMap, texCoords + offsets[4], 0).a;
	float centerSampleRoughness = textureLod(positionMap, texCoords + offsets[4], 0).a;
	result += kernel[4] * centerSample;
	float radiusFactor = roughness;
	
	for(int i = 0; i < 9; i++) {
		if(i == 4) { continue; }
		
		vec4 currentSample = textureLod(sampler, texCoords + radiusFactor*offsets[i], 0);
		float currentSampleDepth = textureLod(normalMap, texCoords + radiusFactor*offsets[i], 0).a;
		float currentSampleRoughness = textureLod(positionMap, texCoords + radiusFactor*offsets[i], 0).a;
		
		float closeness = 1-(distance(currentSampleRoughness, centerSampleRoughness) + distance(currentSampleDepth, centerSampleDepth))/2;
		closeness = 1-(distance(currentSampleRoughness, centerSampleRoughness));
		float sampleWeight = kernel[i] * closeness;
		result += sampleWeight * currentSample;
		
		normalization += (1-closeness)*kernel[i]; // this is the amount we have lost.
	}
	
	return result + normalization * centerSample;
}

vec4 imageSpaceGatherReflection(sampler2D sampler, vec2 texCoords, float roughness) {
	vec2 poissonDisk[64];
	poissonDisk[0] = vec2(-0.613392, 0.617481);
	poissonDisk[1] = vec2(0.170019, -0.040254);
	poissonDisk[2] = vec2(-0.299417, 0.791925);
	poissonDisk[3] = vec2(0.645680, 0.493210);
	poissonDisk[4] = vec2(-0.651784, 0.717887);
	poissonDisk[5] = vec2(0.421003, 0.027070);
	poissonDisk[6] = vec2(-0.817194, -0.271096);
	poissonDisk[7] = vec2(-0.705374, -0.668203);
	poissonDisk[8] = vec2(0.977050, -0.108615);
	poissonDisk[9] = vec2(0.063326, 0.142369);
	poissonDisk[10] = vec2(0.203528, 0.214331);
	poissonDisk[11] = vec2(-0.667531, 0.326090);
	poissonDisk[12] = vec2(-0.098422, -0.295755);
	poissonDisk[13] = vec2(-0.885922, 0.215369);
	poissonDisk[14] = vec2(0.566637, 0.605213);
	poissonDisk[15] = vec2(0.039766, -0.396100);
	poissonDisk[16] = vec2(0.751946, 0.453352);
	poissonDisk[17] = vec2(0.078707, -0.715323);
	poissonDisk[18] = vec2(-0.075838, -0.529344);
	poissonDisk[19] = vec2(0.724479, -0.580798);
	poissonDisk[20] = vec2(0.222999, -0.215125);
	poissonDisk[21] = vec2(-0.467574, -0.405438);
	poissonDisk[22] = vec2(-0.248268, -0.814753);
	poissonDisk[23] = vec2(0.354411, -0.887570);
	poissonDisk[24] = vec2(0.175817, 0.382366);
	poissonDisk[25] = vec2(0.487472, -0.063082);
	poissonDisk[26] = vec2(-0.084078, 0.898312);
	poissonDisk[27] = vec2(0.488876, -0.783441);
	poissonDisk[28] = vec2(0.470016, 0.217933);
	poissonDisk[29] = vec2(-0.696890, -0.549791);
	poissonDisk[30] = vec2(-0.149693, 0.605762);
	poissonDisk[31] = vec2(0.034211, 0.979980);
	poissonDisk[32] = vec2(0.503098, -0.308878);
	poissonDisk[33] = vec2(-0.016205, -0.872921);
	poissonDisk[34] = vec2(0.385784, -0.393902);
	poissonDisk[35] = vec2(-0.146886, -0.859249);
	poissonDisk[36] = vec2(0.643361, 0.164098);
	poissonDisk[37] = vec2(0.634388, -0.049471);
	poissonDisk[38] = vec2(-0.688894, 0.007843);
	poissonDisk[39] = vec2(0.464034, -0.188818);
	poissonDisk[40] = vec2(-0.440840, 0.137486);
	poissonDisk[41] = vec2(0.364483, 0.511704);
	poissonDisk[42] = vec2(0.034028, 0.325968);
	poissonDisk[43] = vec2(0.099094, -0.308023);
	poissonDisk[44] = vec2(0.693960, -0.366253);
	poissonDisk[45] = vec2(0.678884, -0.204688);
	poissonDisk[46] = vec2(0.001801, 0.780328);
	poissonDisk[47] = vec2(0.145177, -0.898984);
	poissonDisk[48] = vec2(0.062655, -0.611866);
	poissonDisk[49] = vec2(0.315226, -0.604297);
	poissonDisk[50] = vec2(-0.780145, 0.486251);
	poissonDisk[51] = vec2(-0.371868, 0.882138);
	poissonDisk[52] = vec2(0.200476, 0.494430);
	poissonDisk[53] = vec2(-0.494552, -0.711051);
	poissonDisk[54] = vec2(0.612476, 0.705252);
	poissonDisk[55] = vec2(-0.578845, -0.768792);
	poissonDisk[56] = vec2(-0.772454, -0.090976);
	poissonDisk[57] = vec2(0.504440, 0.372295);
	poissonDisk[58] = vec2(0.155736, 0.065157);
	poissonDisk[59] = vec2(0.391522, 0.849605);
	poissonDisk[60] = vec2(-0.620106, -0.328104);
	poissonDisk[61] = vec2(0.789239, -0.419965);
	poissonDisk[62] = vec2(-0.545396, 0.538133);
	poissonDisk[63] = vec2(-0.178564, -0.596057);
	
	vec4 result = textureLod(sampler, texCoords, 0).rgba;
	
	vec4 centerSample = result;
	float centerSampleRoughness = roughness;
	float centerSampleDepth = textureLod(normalMap, texCoords, 0).a;
	
	const float NUM_SAMPLES = 32;
	float radiusFactor = roughness * (centerSampleDepth) * 0.1;
	float normalization = 0.0;
	for(int i = 0; i < NUM_SAMPLES; i++) {
		vec4 currentSample = textureLod(sampler, texCoords + radiusFactor * poissonDisk[i], 0);
		float currentSampleRoughness = textureLod(positionMap, texCoords + radiusFactor * poissonDisk[i], 0).a;
		float currentSampleDepth = textureLod(normalMap, texCoords + radiusFactor * poissonDisk[i], 0).a;
		
		float closeness = 1-(distance(currentSampleRoughness, centerSampleRoughness) + distance(currentSampleDepth, centerSampleDepth));
		closeness *= closeness;
		float sampleWeight = closeness;
		result += sampleWeight * currentSample;
		
		normalization += (1-closeness); // this is the amount we have lost.
	}
	
	return (result + normalization*centerSample) / (NUM_SAMPLES+1);
}


const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDirection, vec3 diffuseColor, vec3 reflectedColor, vec3 albedo, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
 	vec3 L = -lightDirection;
    vec3 H = normalize(L + V);
    vec3 N = normal;
    vec3 P = position;
    float NdotH = max(dot(N, H), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float VdotH = max(dot(V, H), 0.0);
    
	
	float alpha = acos(NdotH);
	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	alpha = roughness*roughness;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
    
    // Schlick
	float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	vec3 diff = diffuseColor * albedo;
	
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
	
	diff = diff * (1-fresnel); // enegy conservation between diffuse and spec http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	
	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	cookTorrance = 1; // TODO: Fix this in the reflections shader
	return diff + reflectedColor * specularColor * cookTorrance;
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
vec3 hemisphereSample_uniform(float u, float v, vec3 N) {
    const float PI = 3.1415926536;
     float phi = u * 2.0 * PI;
     float cosTheta = 1.0 - v;
     float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
     vec3 result = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);

	vec3 UpVector = abs(N.z) < 0.999 ? vec3(0,0,1) : vec3(1,0,0);
	vec3 TangentX = normalize( cross( UpVector, N ) );
	vec3 TangentY = cross( N, TangentX );
	 // Tangent to world space
	 result = TangentX * result.x + TangentY * result.y + N * result.z;
     //mat3 transform = createOrthonormalBasis(N);
	 //result = (transform) * result;

     return result;
}
const vec3 inverseGamma = vec3(1/2.2,1/2.2,1/2.2);
void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec4 positionRoughness = textureLod(positionMap, st, 0);
  	float glossiness = positionRoughness.w;
  	float roughness = (1-glossiness);
  	vec3 positionView = positionRoughness.xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec4 normalAmbient = textureLod(normalMap,st, 0);
  	vec4 normalView = vec4(normalAmbient.rgb, 0);
  	vec3 normalWorld = normalize(inverse(viewMatrix) * normalView).xyz;
	vec3 V = -normalize((positionWorld.xyz - camPosition.xyz).xyz);
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;
  	
  	vec4 motionVecProbeIndices = textureLod(motionMap, st, 0);
  	vec2 motion = motionVecProbeIndices.xy;
  	float transparency = motionVecProbeIndices.a;
  	vec4 colorMetallic = textureLod(diffuseMap, st, 0);
  	colorMetallic.xyz = pow(colorMetallic.xyz, inverseGamma);
  	
  	float metallic = colorMetallic.a;

  	vec3 specularColor = mix(vec3(0.04,0.04,0.04), colorMetallic.rgb, metallic);
  	vec3 color = mix(colorMetallic.xyz, vec3(0,0,0), clamp(metallic, 0, 1));
  	
	vec4 lightDiffuseSpecular = textureLod(lightAccumulationMap, st, 0) + textureLod(indirectHalfScreen, st, 0);
	vec4 reflection = textureLod(environmentReflection, st, 0);
	vec3 specularLighting = specularColor.rgb * reflection.rgb * roughness;
	lightDiffuseSpecular.rgb += specularLighting;

	float revealage = textureLod(forwardRenderedRevealageMap, st, 0).r;
	float additiveness = textureLod(forwardRenderedRevealageMap, st, 0).a;
	vec4 forwardRenderedAccum = textureLod(forwardRenderedMap, st, 0);
	vec4 averageColor = vec4(forwardRenderedAccum.rgb / max(forwardRenderedAccum.a, 0.00001), revealage);
	float resultingRevealage = 1 - averageColor.a;
	float resultingAdditiveness = ((additiveness * (1-resultingRevealage)) / 4) + additiveness * resultingRevealage;
	resultingAdditiveness += min(2*(1-resultingRevealage), 1);

	vec4 AOscattering = textureLod(aoScattering, st, 3);
	vec3 scattering = textureLod(aoScattering, st, 2).gba;//AOscattering.gba;

//	vec4 refracted = textureLod(refractedMap, st, 0).rgba;
	//environmentColorAO = bilateralBlur(diffuseEnvironment, st).rgba;
	//environmentColor = imageSpaceGatherReflection(diffuseEnvironment, st, roughness).rgb;
	vec4 environmentLightAO = blur(environment, st, 0, 0.05);
//	environmentLightAO.rgb += refracted.rgb;
	vec3 environmentLight = environmentLightAO.rgb;
//	environmentLight += vec3(0.25f) * color.rgb;
	float ao = AOscattering.r;
	//environmentLight = bilateralBlurReflection(environment, st, roughness).rgb;

	vec3 ambientTerm = ambientColor*environmentLight;
	if(useAmbientOcclusion) {
		ambientTerm *= clamp(ao,0,1);
	}

	vec4 lit = roughness * vec4(ambientTerm.rgb,1) + lightDiffuseSpecular;
//	vec4 lit = max(vec4(ambientTerm, 1),((vec4(diffuseTerm, 1))) + vec4(specularTerm,1));
	out_color = lit;
//	out_color.rgb = mix(out_color.rgb, refracted.rgb, transparency);
	out_color.rgb = out_color.rgb * (1-resultingRevealage) + (resultingAdditiveness * averageColor.rgb);// * (1-resultingRevealage);

	out_color.rgb += (scattering.rgb);

	float autoExposure = exposure;
	if(!AUTO_EXPOSURE_ENABLED) { autoExposure = worldExposure; }

	out_color *= autoExposure;

	const bool toneMap = true;
	if(toneMap) {
        const float EXPOSURE_BIAS = 1;
        out_color.rgb = Uncharted2Tonemap(EXPOSURE_BIAS*out_color.rgb);
        const float maxValue = 4;
        vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(maxValue)); // whitescale marks the maximum value we can have before tone mapping
	    out_color.rgb = out_color.rgb * whiteScale;
    }
	out_color.a = 1;
}
