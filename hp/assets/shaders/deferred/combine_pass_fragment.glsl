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


layout(binding=170) uniform samplerCube probe170;
layout(binding=171) uniform samplerCube probe171;
layout(binding=172) uniform samplerCube probe172;
layout(binding=173) uniform samplerCube probe173;
layout(binding=174) uniform samplerCube probe174;
layout(binding=175) uniform samplerCube probe175;
layout(binding=176) uniform samplerCube probe176;
layout(binding=177) uniform samplerCube probe177;
layout(binding=178) uniform samplerCube probe178;
layout(binding=179) uniform samplerCube probe179;
layout(binding=180) uniform samplerCube probe180;
layout(binding=181) uniform samplerCube probe181;
layout(binding=182) uniform samplerCube probe182;
layout(binding=183) uniform samplerCube probe183;
layout(binding=184) uniform samplerCube probe184;
layout(binding=185) uniform samplerCube probe185;
layout(binding=186) uniform samplerCube probe186;
layout(binding=187) uniform samplerCube probe187;
layout(binding=188) uniform samplerCube probe188;
layout(binding=189) uniform samplerCube probe189;
layout(binding=190) uniform samplerCube probe190;
layout(binding=191) uniform samplerCube probe191;

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

vec4 blur(sampler2D sampler, vec2 texCoords) {
	vec4 result = vec4(0,0,0,0);
	const float scaleX = 1;
	const float scaleY = 1;
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
		
		float closeness = distance(currentSampleDepth, centerSampleDepth);
		float sampleWeight = kernel[i] * closeness;
		result += sampleWeight * currentSample;
		
		normalization += (1-closeness)*kernel[i]; // this is the amount we have lost.
	}
	
	return result + normalization * centerSample;
}


const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDirection, vec3 lightColor) {
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
	F0 = max(F0, ((1-roughness)*0.2));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightColor.rgb) * NdotL;
	//diff = (diff.rgb/3.1416) * (1-F0);
	//diff *= (1/3.1416*alpha*alpha);
	
	float specularAdjust = length(lightColor.rgb)/length(vec3(1,1,1));
	
	return vec4((diff), specularAdjust*(F*D*G/(4*(NdotL*NdotV))));
}

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec4 positionRoughness = textureLod(positionMap, st, 0);
  	float roughness = positionRoughness.w;
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
  	
	const float metalSpecularBoost = 1.4;
  	vec3 specularColor = mix(vec3(0.04,0.04,0.04), metalSpecularBoost*colorMetallic.rgb, metallic);
  	const float metalBias = 0.1;
  	vec3 color = mix(colorMetallic.xyz, vec3(0,0,0), clamp(metallic - metalBias, 0, 1));
  	
	vec4 lightDiffuseSpecular = texture(lightAccumulationMap, st);
	float specularFactor = clamp(lightDiffuseSpecular.a, 0, 1);
	
	vec4 aoReflect = textureLod(aoReflection, st, 1);

	vec4 environmentColorAO = textureLod(diffuseEnvironment, st, 0).rgba;
	environmentColorAO = bilateralBlur(diffuseEnvironment, st).rgba;
	vec3 environmentColor = environmentColorAO.rgb;
	float ao = environmentColorAO.a;
	vec3 reflectedColor = textureLod(specularEnvironment, st, 0).rgb;
	
	float reflectionMixer = (1-roughness); // the glossier, the more reflecting
	reflectionMixer -= (metallic); // metallic reflections should be tinted
	reflectionMixer = clamp(reflectionMixer, 0, 1);
	vec3 finalColor = mix(color, reflectedColor, reflectionMixer);
	vec3 specularTerm = 2*specularColor * specularFactor;
	vec3 diffuseTerm = 2*lightDiffuseSpecular.rgb*finalColor;
	
	vec3 ambientTerm = ambientColor * finalColor.rgb;// + 0.1* reflectedColor;
	
	vec3 ambientSpecular = vec3(0,0,0);
	vec4 ambientFromEnvironment = cookTorrance(-normalize(positionView), positionView, normalView.xyz, roughness, metallic, -normalWorld.xyz, environmentColor);
	ambientSpecular += clamp(ambientFromEnvironment.w, 0, 1) * reflectedColor;
	
	//ambientTerm = 0.5 * ambientColor * (finalColor.rgb * textureLod(getProbeForIndex(probeIndex), normalBoxProjected,9).rgb * max(dot(normalWorld, normalBoxProjected), 0.0) + textureLod(getProbeForIndex(probeIndex), normalBoxProjected,9).rgb*max(dot(reflect(V, normalWorld), -normalBoxProjected), 0.0));
	ambientTerm = 2*ambientColor * finalColor * ambientFromEnvironment.xyz + 2*ambientColor * specularColor * ambientSpecular;

	ambientTerm *= clamp(ao,0,1);
	vec4 lit = vec4(ambientTerm, 1) + ((vec4(diffuseTerm, 1))) + vec4(specularTerm,1);
	//vec4 lit = max(vec4(ambientTerm, 1),((vec4(diffuseTerm, 1))) + vec4(specularTerm,1));
	out_color = lit;
	out_color.rgb += (aoReflect.gba);
	out_color *= exposure/2;
	
	out_color.rgb = Uncharted2Tonemap(out_color.rgb);
	vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(11.2,11.2,11.2)* 0.15);
	out_color.rgb = out_color.rgb * whiteScale;
	/////////////////////////////// GAMMA
	//out_color.r = pow(out_color.r,1/2.2);
	//out_color.g = pow(out_color.g,1/2.2);
	//out_color.b = pow(out_color.b,1/2.2);
	
	//out_color.rgb *= aoReflect.gba;
	//out_color.rgb = vec3(specularFactor,specularFactor,specularFactor);
	//out_color.rgb = normalView.xyz;
	//out_color.rgb = finalColor.xyz;
	//out_color.rgb = lightDiffuseSpecular.rgb;
	//out_color.rgb = vec3(motionVec,0);
	//out_color.rgb = specularTerm.rgb;
	//out_color.rgb = vec3(roughness,roughness,roughness);
	//out_color.rgb = specularTerm;
	//out_color.rgb = vec3(ao,ao,ao);
	//out_color.rgb = environmentColor.rgb;
	//out_color.rgb = reflectedColor;
	//out_color.rgb = texture(probes, vec4(normalWorld, 1), 0).rgb;
	
	/* if(probeIndex == 191) {
		out_color.rgb = vec3(1,0,0);
	} else if(probeIndex == 190) {
		out_color.rgb = vec3(0,1,0);
	} else if(probeIndex == 189) {
		out_color.rgb = vec3(0,0,1);
	} else if(probeIndex == 0) {
		out_color.rgb = vec3(1,0,1);
	} */
}
