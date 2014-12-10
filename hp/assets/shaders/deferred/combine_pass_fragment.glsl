#version 420

layout(binding=0) uniform sampler2D diffuseMap; // diffuse, metallic 
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D aoReflection; // ao, reflectedColor
layout(binding=3) uniform sampler2D motionMap; // motionVec
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth
layout(binding=6) uniform samplerCube globalEnvironmentMap;
layout(binding=7) uniform samplerCubeArray probes;
layout(binding=8) uniform sampler2D diffuseEnvironment; // probe sample, ambient occlusion
layout(binding=9) uniform sampler2D specularEnvironment; // reflection

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 projectionMatrix;
uniform vec3 camPosition;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform int exposure = 4;

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
vec4 blur(sampler2D sampler, vec2 texCoords) {
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * texture(sampler, texCoords + offsets[0]);
	result += kernel[1] * texture(sampler, texCoords + offsets[1]);
	result += kernel[2] * texture(sampler, texCoords + offsets[2]);
	
	result += kernel[3] * texture(sampler, texCoords + offsets[3]);
	result += kernel[4] * texture(sampler, texCoords + offsets[4]);
	result += kernel[5] * texture(sampler, texCoords + offsets[5]);
	
	result += kernel[6] * texture(sampler, texCoords + offsets[6]);
	result += kernel[7] * texture(sampler, texCoords + offsets[7]);
	result += kernel[8] * texture(sampler, texCoords + offsets[8]);
	
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
vec4 bilateralBlurReflection(sampler2D sampler, vec2 texCoords) {

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
	
	for(int i = 0; i < 9; i++) {
		if(i == 4) { continue; }
		
		vec4 currentSample = textureLod(sampler, texCoords + offsets[i], 0);
		float currentSampleDepth = textureLod(normalMap, texCoords + offsets[i], 0).a;
		float currentSampleRoughness = textureLod(positionMap, texCoords + offsets[i], 0).a;
		
		float closeness = 1-(distance(currentSampleRoughness, centerSampleRoughness) + distance(currentSampleDepth, centerSampleDepth))/2;
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
	float centerSampleDepth = textureLod(normalMap, texCoords, 0).a;
	
	const float NUM_SAMPLES = 16;
	float radiusFactor = roughness * 0.05;
	float normalization = 0.0;
	for(int i = 0; i < NUM_SAMPLES; i++) {
		vec4 currentSample = textureLod(sampler, texCoords + radiusFactor * poissonDisk[i], 0);
		float currentSampleDepth = textureLod(normalMap, texCoords + poissonDisk[i], 0).a;
		
		float closeness = 1-distance(currentSampleDepth, centerSampleDepth);
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

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDirection, vec3 lightColor, vec3 reflectedColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	V = ViewVector;
 	vec3 L = -normalize((viewMatrix*vec4(lightDirection, 0)).xyz);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = max(dot(N, H), 0.0);
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float VdotH = max(dot(V, H), 0.0);
    
	
	float alpha = acos(NdotH);
	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	//alpha = roughness*roughness;
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
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightColor.rgb) * NdotL;
	diff = diff * (1-F0); // enegy conservation between diffuse and spec http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	
	
	float specularAdjust = length(lightColor.rgb)/length(vec3(1,1,1));
	
	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	
	return diff + reflectedColor *cookTorrance;
	//return vec4((diff), specularAdjust*(F*D*G/(4*(NdotL*NdotV))));
}

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec4 positionRoughness = textureLod(positionMap, st, 0);
  	float roughness = positionRoughness.w;
  	float glossiness = (1-roughness);
  	vec3 positionView = positionRoughness.xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
  	vec4 normalView = vec4(texture2D(normalMap,st).rgb, 0);
  	vec3 normalWorld = normalize(inverse(viewMatrix) * normalView).xyz;
	vec3 V = -normalize((positionWorld.xyz - camPosition.xyz).xyz);
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;
  	
  	vec4 motionVecProbeIndices = texture2D(motionMap, st); 
  	vec2 motionVec = motionVecProbeIndices.xy;
  	vec4 colorMetallic = texture2D(diffuseMap, st);
  	
  	float metallic = colorMetallic.a;
  	
	const float metalSpecularBoost = 1.0;
  	vec3 specularColor = mix(vec3(0.04,0.04,0.04), metalSpecularBoost*colorMetallic.rgb, metallic);
  	const float metalBias = 0.1;
  	vec3 color = mix(colorMetallic.xyz, vec3(0,0,0), clamp(metallic - metalBias, 0, 1));
  	
	vec4 lightDiffuseSpecular = texture(lightAccumulationMap, st);
	float specularFactor = clamp(lightDiffuseSpecular.a, 0, 1);
	
	vec4 aoReflect = textureLod(aoReflection, st, 1);

	vec4 environmentColorAO = textureLod(diffuseEnvironment, st, 0).rgba;
	//environmentColorAO = bilateralBlur(diffuseEnvironment, st).rgba;
	vec3 environmentColor = clamp(environmentColorAO.rgb, vec3(0,0,0), vec3(1,1,1));
	environmentColor = bilateralBlurReflection(diffuseEnvironment, st).rgb;
	//environmentColor = imageSpaceGatherReflection(diffuseEnvironment, st, roughness).rgb;
	float ao = environmentColorAO.a;
	vec3 reflectedColor = clamp(textureLod(specularEnvironment, st, 0).rgb, vec3(0,0,0), vec3(1,1,1));
	reflectedColor = bilateralBlurReflection(specularEnvironment, st).rgb;
	//reflectedColor = imageSpaceGatherReflection(specularEnvironment, st, roughness).rgb;
	
	float reflectionMixer = glossiness; // the glossier, the more reflecting, so glossiness is our mixer
	vec3 specularTerm = 2*specularColor * specularFactor;
	vec3 diffuseTerm = 2*lightDiffuseSpecular.rgb*color;
	
	vec3 ambientTerm = ambientColor * mix(color.rgb, reflectedColor.rgb, reflectionMixer);
	
	vec3 ambientDiffuseSpecular = cookTorrance(-normalize(positionView), positionView, normalView.xyz, roughness, metallic, -normalWorld.xyz, environmentColor, specularColor * reflectedColor);
	
	ambientTerm = 2*ambientColor*ambientDiffuseSpecular * color.rgb;

	ambientTerm *= clamp(ao,0,1);
	vec4 lit = vec4(ambientTerm, 1) + vec4(diffuseTerm, 1) + vec4(specularTerm,1);
	//vec4 lit = max(vec4(ambientTerm, 1),((vec4(diffuseTerm, 1))) + vec4(specularTerm,1));
	out_color = lit;
	out_color.rgb += (aoReflect.gba); //scattering
	
	out_color *= exposure/2;
	
	out_color.rgb = Uncharted2Tonemap(out_color.rgb);
	vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(2.2,2.2,2.2));
	out_color.rgb = out_color.rgb * whiteScale;
	/////////////////////////////// GAMMA
	//out_color.r = pow(out_color.r,1/2.2);
	//out_color.g = pow(out_color.g,1/2.2);
	//out_color.b = pow(out_color.b,1/2.2);
	
	//out_color.rgb *= aoReflect.gba;
	//out_color.rgb = vec3(specularFactor,specularFactor,specularFactor);
	//out_color.rgb = normalView.xyz;
	//out_color.rgb = specularColor.xyz;
	//out_color.rgb = lightDiffuseSpecular.rgb;
	//out_color.rgb = vec3(motionVec,0);
	//out_color.rgb = ambientTerm.rgb;
	//out_color.rgb = vec3(roughness,roughness,roughness);
	//out_color.rgb = specularTerm;
	//out_color.rgb = vec3(ao,ao,ao);
	//out_color.rgb = environmentColor.rgb;
	//out_color.rgb = reflectedColor.rgb;
	//out_color.rgb = texture(probes, vec4(normalWorld, 0), 0).rgb;
	//out_color.rgb = texture(globalEnvironmentMap, normalWorld, 0).rgb;
	
	/*int probeIndex = int(textureLod(motionMap, st, 0).x);
	if(probeIndex == 191) {
		out_color.rgb = vec3(1,0,0);
	} else if(probeIndex == 190) {
		out_color.rgb = vec3(0,1,0);
	} else if(probeIndex == 189) {
		out_color.rgb = vec3(0,0,1);
	} else if(probeIndex == 0) {
		out_color.rgb = vec3(1,0,1);
	}*/
}
