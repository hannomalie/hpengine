#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D lightAccumulationMap;
layout(binding=2) uniform sampler2D normalMap;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

out vec4 out_color;

uniform bool useAmbientOcclusion = false;
uniform float ambientOcclusionRadius = 0.006;
uniform float ambientOcclusionTotalStrength = 0.38;
uniform float ambientOcclusionStrength = 0.07;
uniform float ambientOcclusionFalloff = 0.000002;

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec3 color = texture2D(diffuseMap, st).xyz;
	vec3 light = texture2D(lightAccumulationMap, st).xyz;
	vec4 normalAndDepth = texture2D(normalMap, st);
	vec3 normal = normalAndDepth.xyz;
	float depth = normalAndDepth.w;
	
	float ao = 1;
	if (useAmbientOcclusion && ambientOcclusionTotalStrength != 0) {
		vec3 N = normal;
		float falloff = ambientOcclusionFalloff;
		const float samples = 15;
		float invSamples = 1/samples;
		
		vec3 fres = normalize(rand(color.rg)*2) - vec3(1.0, 1.0, 1.0);
		vec3 ep = vec3(st, depth);
		float rad = ambientOcclusionRadius;
		float radD = rad/depth;
		float bl = 0.0f;
		float occluderDepth, depthDifference, normDiff;
		float totStrength = ambientOcclusionTotalStrength;
		float strength = ambientOcclusionStrength;
	
	
		for(int i=0; i<samples;++i) {
		  vec3 ray = radD*reflect(pSphere[i],fres);
		  vec3 se = ep + sign(dot(ray,N) )*ray;
		  vec4 occluderFragment = texture2D(normalMap,se.xy);
		  vec3 occNorm = occluderFragment.xyz;
		
		  // Wenn die Diff der beiden Punkte negativ ist, ist der Verdecker hinter dem aktuellen Bildpunkt
		  depthDifference = depth-occluderFragment.w;
		
		  // Berechne die Differenz der beiden Normalen als ein Gewicht
		  normDiff = (1.0-dot(occNorm,N));
		
		  // the falloff equation, starts at falloff and is kind of 1/x^2 falling
		  bl += step(falloff,depthDifference)*normDiff*(1.0-smoothstep(falloff,strength,depthDifference));
	
	    }
	    
		ao = 1.0-totStrength*bl*invSamples;
	}
	out_color = vec4(color * (light - (1- ao)) , 1);
  	//out_color = vec4(ao,ao,ao,1);
}
