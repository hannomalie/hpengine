#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;
layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2
layout(binding=7) uniform sampler2D shadowMapWorldPosition; // world position

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

in vec2 pass_TextureCoord;
out vec4 out_DiffuseSpecular;
out vec4 out_AOReflection;
out vec4 out_DiffuseIndirect;

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

vec4 phong (in vec3 position, in vec3 normal, in vec4 color, in vec4 specular, vec3 probeColor, float roughness) {
  vec3 direction_to_light_eye = normalize((viewMatrix*vec4(lightDirection, 0)).xyz);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  normal), 0.0);
  
  // standard specular light
  vec3 reflection_eye = reflect (-direction_to_light_eye, normal);
  vec3 surface_to_viewer_eye = normalize (-position);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  int specularPower = int(2048 * (1-roughness) + 1); //specular.a
  float specular_factor = clamp(pow (dot_prod_specular, (specularPower)), 0, 1);
  
  vec3 environmentSample = texture(environmentMap, normal).rgb;
  //return vec4((vec4(environmentSample,1) * dot_prod).xyz, specular_factor);
  vec3 lightColor = lightDiffuse;
  return vec4((vec4(lightColor,1) * dot_prod).xyz, specular_factor);
}

vec4 brdf(in vec3 position, in vec3 normal, in vec4 color, in vec4 specular, vec3 probeColor, float roughness) {
//http://en.wikibooks.org/wiki/GLSL_Programming/Unity/Specular_Highlights_at_Silhouettes
	vec3 normalDirection = normalize(normal);
 
    vec3 viewDirection = normalize(-position);
    vec3 ambientLighting = vec3(0,0,0);
 
 	vec3 lightDir = normalize((viewMatrix*vec4(lightDirection, 0)).xyz);
    vec3 environmentSample = texture(environmentMap, normalDirection).rgb;
    vec3 diffuseReflection = lightDiffuse * max(0.0, dot(normalDirection, lightDir));
 
    vec3 specularReflection;
    if (dot(normalDirection, lightDir) < 0.0) {
       specularReflection = vec3(0.0, 0.0, 0.0); 
    } else {
       vec3 halfwayDirection = normalize(lightDir + viewDirection);
       //float w = pow(1.0 - max(0.0,  dot(halfwayDirection, viewDirection)), 5.0);
       //specularReflection = lightDiffuse.rgb
       //   * mix(specular.rgb, vec3(1.0), w) 
       //   * pow(max(0.0, dot(reflect(lightDir, normalDirection), viewDirection)), specular.a);
       int specularPower = int(2048 * (1-roughness) + 1); //specular.a
       specularReflection = lightDiffuse.rgb * pow(max(0.0, dot(halfwayDirection, viewDirection)), specularPower);
    }
 
    //return vec4(ambientLighting + diffuseReflection + specularReflection, 1.0);
    return vec4(ambientLighting + diffuseReflection, clamp(length(specularReflection),0,1));
}

vec4 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic) {
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	//V = ViewVector;
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
	alpha = roughness*roughness;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));
	
	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));
    
    // Schlick
	float F0 = 0.04;
	// Specular in the range of 0.02 - 0.2
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	//F0 = max(F0, metallic);
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;
	
	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	//diff = (diff.rgb/3.1416) * (1-F0);
	//diff *= (1/3.1416*alpha*alpha);
	
	float specularAdjust = length(lightDiffuse.rgb)/length(vec3(1,1,1));
	
	return vec4((diff), specularAdjust*(F*D*G/(4*(NdotL*NdotV))));
}

///////////////////// AO
uniform bool useAmbientOcclusion = false;
uniform float ambientOcclusionRadius = 0.006;
uniform float ambientOcclusionTotalStrength = 0.38;
uniform float ambientOcclusionStrength = 0.7;
uniform float ambientOcclusionFalloff = 0.001;

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
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
vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	/*if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return 0;
	}*/
	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = texture2D(shadowMap,ShadowCoordPostW.xy);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;
	moments = blurSample(shadowMap, ShadowCoordPostW.xy, moments.y * 0.001).rg;
	//moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y * 0.002).rg;
	//moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y * 0.005).rg;
	//moments /= 3;
	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist < moments.x) {
		return vec3(1.0,1.0,1.0);
	}

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.00012);

	float d = dist - moments.x;
	float p_max = (variance / (variance + d*d));

	return vec3(p_max,p_max,p_max);
}

vec3 getIndirectLight(vec4 ShadowCoordPostW, vec3 positionShadow, vec3 positionWorld, vec3 normalWorld, float depthInLightSpace) {
//return vec3(0,0,0);
	const float NUM_SAMPLES_FACTOR = 40;
	const float DISTFLOAT = 0.5;
	const vec2 DIST = vec2(DISTFLOAT,DISTFLOAT);
	
	vec3 shadowNormal = decode(texture(shadowMap, ShadowCoordPostW.xy).ba);
	float sum = 0;//max(dot(normalWorld,shadowNormal), 0);
	
	for(int i = 1; i <= NUM_SAMPLES_FACTOR; i++) {
		float tempSum = 0;
		
		for(int z = 0; z < 16; z++) {
			vec2 coords = ShadowCoordPostW.xy + (i/NUM_SAMPLES_FACTOR)*pSphere[z].xy*DIST;
			vec4 shadowMapSample = texture(shadowMap, coords);
			vec4 shadowWorldPosition = texture(shadowMapWorldPosition, coords);
			float shadowDepth = shadowMapSample.x;
			shadowNormal = decode(shadowMapSample.ba);
			float flux = 1;
			flux *= max(0,dot(shadowNormal, -lightDirection));
			flux *= max(0,dot(shadowNormal, (positionWorld - shadowWorldPosition.xyz)));
			flux *= max(0,dot(normalWorld, (shadowWorldPosition.xyz - positionWorld)));
			
			tempSum += flux/pow(length(positionWorld - shadowWorldPosition.xyz),1);
		}
		//sum += tempSum/16;
		sum += tempSum;
	}

	sum /= NUM_SAMPLES_FACTOR;
	
	return vec3(sum,sum,sum);
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
  			if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1)
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

/////////////////////

float ComputeScattering(float lightDotView)
{
	const float G_SCATTERING = 0.00005;
	const float PI = 3.1415926536f;
	float result = 1.0f - G_SCATTERING;
	result *= result;
	result /= (4.0f * PI * pow(1.0f + G_SCATTERING * G_SCATTERING - (2.0f * G_SCATTERING) * lightDotView, 1.5f));
	return result;
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
	 
	for (int i = 0; i < NB_STEPS; i++)
	{
		vec4 shadowPos = shadowMatrix * vec4(currentPosition, 1.0f);
		vec4 worldInShadowCameraSpace = shadowPos;
		worldInShadowCameraSpace /= worldInShadowCameraSpace.w;
    	vec2 shadowmapTexCoord = (worldInShadowCameraSpace.xy * 0.5 + 0.5);
    	
		float shadowMapValue = textureLod(shadowMap, shadowmapTexCoord,0).r;
		 
		if (shadowMapValue > worldInShadowCameraSpace.z)
		{
			accumFog += ComputeScattering(dot(rayDirection, lightDirection));
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
	vec4 probeColorDepth = texture2D(probe, st);
	vec3 probeColor = probeColorDepth.rgb;
	float probeDepth = probeColorDepth.a;
	float roughness = texture2D(positionMap, st).a;
	
  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	
	// skip background
	if (positionView.z > -0.0001) {
	  discard;
	}
	vec3 normalView = texture2D(normalMap, st).xyz;
	//normalView = decodeNormal(normalView.xy);
	
	vec4 specular = texture2D(specularMap, st);
	float metallic = specular.a;
	specular.rgb = mix(vec3(1,1,1), color, metallic);
	//vec4 finalColor = vec4(albedo,1) * ( vec4(phong(position.xyz, normalize(normal).xyz), 1));
	vec4 finalColor = cookTorrance(V, positionView, normalView, roughness, metallic);
	
	/////////////////// SHADOWMAP
	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;

	visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow), 0, 1);

	finalColor *= visibility;

	//finalColor = vec4(visibility,visibility,visibility,visibility);
	/////////////////// SHADOWMAP
	
	float ao = 1;
	vec3 ssdo = vec3(0,0,0);
	if (useAmbientOcclusion && ambientOcclusionTotalStrength != 0) {
		vec3 N = normalView;
		float falloff = ambientOcclusionFalloff;
		const float samples = 16;
		const float invSamples = 1/samples;
		
		vec3 fres = normalize(rand(color.rg)*2) - vec3(1.0, 1.0, 1.0);
		vec3 ep = vec3(st, depth);
		float rad = 1000*ambientOcclusionRadius;
		float radD = rad*(1-depth);
		float bl = 0.0f;
		float occluderDepth, depthDifference, normDiff;
		float totStrength = ambientOcclusionTotalStrength;
		float strength = ambientOcclusionStrength;
	
		for(int i=0; i<samples;++i) {
		  vec3 ray = (viewMatrix *vec4(radD*pSphere[i%16],0)).xyz;
		  vec3 se = ep + sign(dot(ray,N) )*ray;
		  vec4 occluderFragment = texture2D(normalMap,se.xy);
		  vec3 occNorm = occluderFragment.xyz;
		  //occNorm = decodeNormal(occNorm.xy);
		
		  // Wenn die Diff der beiden Punkte negativ ist, ist der Verdecker hinter dem aktuellen Bildpunkt
		  depthDifference = (depth-occluderFragment.w);
		
		  // Berechne die Differenz der beiden Normalen als ein Gewicht
		  float dotProd = dot(occNorm,N);
		  normDiff = (1.0-dotProd);
		  
		  //////////
		  vec3 occluderColor = texture2D(diffuseMap, se.xy).xyz;
		  float dotProdSSDO = dot((viewMatrix * vec4(lightDirection, 0)).xyz, occNorm);
		  vec3 occluderLit = occluderColor * dotProdSSDO;
		  ssdo += occluderLit * dotProd;
		  //////////
		  
		  // the falloff equation, starts at falloff and is kind of 1/x^2 falling
		  bl += normDiff*(1.0-smoothstep(falloff,strength,depthDifference));
	    }
	    
		ao = 1.0-totStrength*bl*invSamples;
		ssdo *= ao;
	}
	
	out_DiffuseSpecular = finalColor;// + ambientTerm;
	//vec3 indirectLight = lightDiffuse.rgb*getIndirectLight(positionShadow, (shadowMatrix * vec4(positionWorld.xyz, 1)).rgb, positionWorld, (inverse(viewMatrix) * vec4(normalView,0)).xyz, depthInLightSpace);
	//out_DiffuseIndirect.rgb = indirectLight;
	
	//out_DiffuseSpecular.rgb = (ssdo);
	
	//vec4 reflectedColor = vec4(rayCastReflect(color.xyz, probeColor.xyz, st, positionView, normalView), 0);
	//out_AOReflection = vec4(ao, reflectedColor.rgb);
	out_AOReflection.r = ao;
	out_AOReflection.gba = scatterFactor * 0.5 * scatter(positionWorld, -eyePosition);
	
	//out_DiffuseSpecular.rgb = scatter(positionWorld, -eyePosition);
	//out_DiffuseSpecular = vec4(ssdo,1);
	//out_AOReflection.rgb = vec3(depthInLightSpace,depthInLightSpace,depthInLightSpace);
}
