#version 420

layout(binding=0) uniform sampler2D diffuseMap; // diffuse, reflectiveness 
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D aoReflection; // ao, reflectedColor
layout(binding=3) uniform sampler2D specularMap; // specular color, metallic
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth
layout(binding=6) uniform samplerCube globalEnvironmentMap; // normal, depth


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
vec4 blurSample(sampler2D sampler, vec2 texCoord, float dist) {

	vec4 result = texture2D(sampler, texCoord);
	
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y + dist));
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y));
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y - dist));
	result += texture2D(sampler, vec2(texCoord.x, texCoord.y - dist));
	
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y + dist));
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y));
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y - dist));
	result += texture2D(sampler, vec2(texCoord.x, texCoord.y + dist));
	
	return result/9;
}

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

samplerCube getProbeForIndex(int probeIndex) {

	if(probeIndex == 191) {
		return probe191;
	} else if(probeIndex == 190) {
		return probe190;
	} else if(probeIndex == 189) {
		return probe189;
	} else if(probeIndex == 188) {
		return probe188;
	} else {
		return globalEnvironmentMap;
	}
}

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 rayCastReflect(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView) {

//return color;
//return probeColor;

	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = normalize(reflect(eyeToSurfaceView, targetNormalView));
	
	vec3 viewRay = 10*normalize(reflectionVecView);
	
	vec3 currentViewPos = targetPosView;
	for (int i = 0; i < 25; i++) {
	
		  currentViewPos += viewRay;
		  
		  vec3 currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
		  float difference = currentViewPos.z - currentPosSample.z;
		  if (difference < 0) {
		  	
		  	currentViewPos -= viewRay;
		  	
		  	for(int x = 0; x < 10; x++) {
		 		currentViewPos += viewRay/10;
		  		currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
				  difference = currentViewPos.z - currentPosSample.z;
				  //if (difference < 0 && difference > -2) {
				  if (abs(difference) > 2) {
				  	//float temp = currentPosSample - targetPosView; 
				  	//if(abs(temp) > 2)
				  	{
	  		  		  break;
				  	}
				  }
		  	}
		  	
  		  	vec4 resultCoords = getViewPosInTextureSpace(currentPosSample);
  			//if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1)
			{
    			float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))*2), 0, 1);
    			//float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))-0.5)*2, 0, 1);
    			vec3 reflectedColor =  texture2D(diffuseMap, resultCoords.xy).xyz;
    			//vec3 reflectedColor =  blurSample(diffuseMap, resultCoords.xy, 0.05).rgb;
    			//return vec3(screenEdgefactor, 0, 0);
    			
    			float screenEdgefactorX = clamp(abs(resultCoords.x) - 0.95, 0, 1);
    			float screenEdgefactorY = clamp(abs(resultCoords.y) - 0.95, 0, 1);
    			screenEdgefactor = 20*max(screenEdgefactorX, screenEdgefactorY);
    			//return vec3(screenEdgefactor, 0, 0);
    			
				return mix(probeColor, reflectedColor, 1-screenEdgefactor);
		  	}
		  	//return vec3(1,0,0);
		  	//color = texture(environmentMap, normalize(normalize((inverse(viewMatrix) * vec4(reflectionVecView,0)).xyz))).rgb;
			return probeColor;
		  }
	}
	
	return probeColor;
}


vec3 sslr(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness) {
	vec3 sum = rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView);
	const float factor = 0.025;
	vec3 offset0 = vec3(roughness,roughness,roughness) * factor;
	vec3 offset1 = vec3(roughness,roughness,-roughness) * factor;
	vec3 offset2 = vec3(roughness,-roughness,-roughness) * factor;
	vec3 offset3 = vec3(-roughness,-roughness,-roughness) * factor;
	vec3 offset4 = vec3(-roughness,-roughness,-roughness) * factor;
	
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset0);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset1);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset2);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset3);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset4);
	
	return sum/5;
}

vec3 boxProjection(vec3 position_world, vec3 texCoords3d, vec3 environmentMapMin, vec3 environmentMapMax) {
	vec3 nrdir = normalize(texCoords3d);
	vec3 envMapMin = vec3(-300,-300,-300);
	envMapMin = environmentMapMin;
	vec3 envMapMax = vec3(300,300,300);
	envMapMax = environmentMapMax;
	
	vec3 rbmax = (envMapMax - position_world.xyz)/nrdir;
	vec3 rbmin = (envMapMin - position_world.xyz)/nrdir;
	//vec3 rbminmax = (nrdir.x > 0 && nrdir.y > 0 && nrdir.z > 0) ? rbmax : rbmin;
	vec3 rbminmax;
	rbminmax.x = (nrdir.x>0.0)?rbmax.x:rbmin.x;
	rbminmax.y = (nrdir.y>0.0)?rbmax.y:rbmin.y;
	rbminmax.z = (nrdir.z>0.0)?rbmax.z:rbmin.z;
	float fa = min(min(rbminmax.x, rbminmax.y), rbminmax.z);
	vec3 posonbox = position_world.xyz + nrdir*fa;
	
	//texCoords3d = normalize(posonbox - vec3(0,0,0));
	vec3 environmentMapWorldPosition = (envMapMax + envMapMin)/2;
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec4 positionRoughness = texture2D(positionMap, st);
  	float roughness = positionRoughness.w;
  	vec3 positionView = positionRoughness.xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
  	vec4 normalView = vec4(texture2D(normalMap,st).rgb, 0);
  	vec3 normalWorld = (inverse(viewMatrix) * normalView).xyz;
	vec3 V = -normalize((positionWorld.xyz - camPosition.xyz).xyz);
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;
  	
  	vec4 colorProbeindex = texture2D(diffuseMap, st);
  	int probeIndex = int(colorProbeindex.w);
  	
  	vec4 specularColorMetallic = texture2D(specularMap, st);
  	vec3 specularColor = specularColorMetallic.xyz;
  	float metallic = specularColorMetallic.a;
  	
	const float metalSpecularBoost = 1.5;
  	specularColor = mix(specularColor, metalSpecularBoost*colorProbeindex.rgb, metallic);
  	const float metalBias = 0.1;
  	vec3 color = mix(colorProbeindex.xyz, vec3(0,0,0), clamp(metallic - metalBias, 0, 1));
  	
	vec4 lightDiffuseSpecular = texture2D(lightAccumulationMap, st);
	//lightDiffuseSpecular = blurSample(lightAccumulationMap, st, 0.000015);
	float specularFactor = lightDiffuseSpecular.a;
	
	vec4 aoReflect = texture2D(aoReflection, st);
	float ao = blurSample(aoReflection, st, 0.0025).r;
	ao += blurSample(aoReflection, st, 0.005).r;
	ao /= 2;

	vec3 texCoords3d = normalize(reflect(V, normalWorld));
	texCoords3d = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	
	// Try to parallax-correct
	//texCoords3d -= texCoords3d * 0.00001 * texture(getProbeForIndex(probeIndex), texCoords3d).a;
	//texCoords3d = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	
	
	vec3 reflectedColor = textureLod(getProbeForIndex(probeIndex), texCoords3d, roughness * 9).rgb;
	//reflectedColor = sslr(color, reflectedColor, st, positionView, normalView.rgb, roughness);
	reflectedColor = rayCastReflect(color, reflectedColor, st, positionView, normalView.rgb);
	//reflectedColor /= 2;
	
	float reflectionMixer = (1-roughness); // the glossier, the more reflecting
	vec3 finalColor = mix(color, reflectedColor, reflectionMixer);
	finalColor = mix(finalColor, (1*finalColor+2*specularColor)/3, metallic);
	vec3 specularTerm = specularColor * max(specularFactor,0) + lightDiffuseSpecular.rgb;
	
	finalColor.rgb = Uncharted2Tonemap(finalColor.rgb);
	vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(11.2,11.2,11.2));
	finalColor.rgb = finalColor.rgb * whiteScale;
	/////////////////////////////// GAMMA
	//finalColor.r = pow(finalColor.r,1/2.2);
	//finalColor.g = pow(finalColor.g,1/2.2);
	//finalColor.b = pow(finalColor.b,1/2.2);
	vec3 ambientTerm = ambientColor * finalColor.rgb;// + 0.1* reflectedColor;
	vec3 normalBoxProjected = boxProjection(positionWorld, normalWorld, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	//float attenuation = 1-min(distance(positionWorld,environmentMapMin[probeIndex]), distance(positionWorld,environmentMapMax[probeIndex]))/(distance(environmentMapMin[probeIndex], environmentMapMax[probeIndex]/2));
	//ambientTerm = ambientColor * finalColor.rgb * textureLod(getProbeForIndex(probeIndex), normalBoxProjected,9).rgb;
	ambientTerm *= ao;
	vec4 lit = vec4(ambientTerm, 1) + ((vec4(lightDiffuseSpecular.rgb*finalColor, 1))) * vec4(specularTerm,1);
	out_color = lit;
	out_color.rgb += (aoReflect.gba);
	out_color *= exposure/2;
	
	//out_color.rgb *= aoReflect.gba;
	//out_color.rgb = lightDiffuseSpecular.rgb;
	//out_color.rgb = vec3(specularFactor,specularFactor,specularFactor);
	//out_color.rgb = normalWorld.rgb;
	//out_color.rgb = vec3(roughness,roughness,roughness);
	//out_color.rgb = specularTerm;
	//out_color.rgb = vec3(ao,ao,ao);
	/*if(probeIndex == 191) {
		out_color.rgb = vec3(1,0,0);
	} else if(probeIndex == 190) {
		out_color.rgb = vec3(0,1,0);
	} else if(probeIndex == 189) {
		out_color.rgb = vec3(0,0,1);
	}*/
}
