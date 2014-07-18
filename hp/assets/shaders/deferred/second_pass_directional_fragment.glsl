#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2
uniform float materialSpecularCoefficient = 0;

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
uniform vec3 lightSpecular;

in vec2 pass_TextureCoord;
out vec4 out_DiffuseSpecular;
out vec4 out_AOReflection;

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

vec4 phong (in vec3 position, in vec3 normal, in vec4 color, in vec4 specular) {
  vec3 direction_to_light_eye = normalize((viewMatrix*vec4(lightDirection, 0)).xyz);
  
  // standard diffuse light
  float dot_prod = max (dot (direction_to_light_eye,  normal), 0.0);
  
  // standard specular light
  vec3 reflection_eye = reflect (-direction_to_light_eye, normal);
  vec3 surface_to_viewer_eye = normalize (position);
  float dot_prod_specular = dot (reflection_eye, surface_to_viewer_eye);
  dot_prod_specular = max (dot_prod_specular, 0.0);
  float specular_factor = clamp(pow (dot_prod_specular, length(specular.x)), 0, 1);
  
  //vec3 environmentSample = texture(environmentMap, -normal).rgb;
  //return vec4((vec4(environmentSample,1) * dot_prod).xyz, specular_factor);
  return vec4((vec4(lightDiffuse,1) * dot_prod).xyz, specular_factor);
}

///////////////////// AO
uniform bool useAmbientOcclusion = false;
uniform float ambientOcclusionRadius = 0.006;
uniform float ambientOcclusionTotalStrength = 0.38;
uniform float ambientOcclusionStrength = 0.7;
uniform float ambientOcclusionFalloff = 0.0002;

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
	
	return result/8;

}
float chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return 0;
	}
	// We retrive the two moments previously stored (depth and depth*depth)
	vec2 moments = texture2D(shadowMap,ShadowCoordPostW.xy).rg;
	moments = blurSample(shadowMap, ShadowCoordPostW.xy, moments.y/100).rg;
	moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y/50).rg;
	moments /= 2;
	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist < moments.x)
		return 1.0 ;

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.0001);

	float d = dist - moments.x;
	float p_max = variance / (variance + d*d);

	return p_max;
}

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 rayCastReflect(vec3 color, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView) {
	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = reflect(eyeToSurfaceView, targetNormalView);
	
	vec3 viewRay = 20*normalize(reflectionVecView);
	
	vec3 currentViewPos = targetPosView;
	for (int i = 0; i < 20; i++) {
	
		  currentViewPos += viewRay;
		  
		  vec3 currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
		  float difference = currentViewPos.z - currentPosSample.z;
		  if (difference < 0) {
		  	
		  	currentViewPos -= viewRay;
		  	
		  	for(int x = 0; x < 20; x++) {
		 		currentViewPos += viewRay/20;
		  		currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
				  difference = currentViewPos.z - currentPosSample.z;
				  if (difference < 0) {
	  		  		break;
				  }
		  	}
		  	
  		  	vec4 resultCoords = getViewPosInTextureSpace(currentPosSample);
  			if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1) {
				float minDist = distance(vec2(0.7,0.7) , vec2(0.5, 0.5));
				float amount = clamp(distance(resultCoords.xy , vec2(0.5, 0.5)) - minDist, 0, 1);
    			float screenEdgefactor = amount;
    			//return vec3(screenEdgefactor,0,0);
    			vec3 reflectedColor =  texture2D(diffuseMap, resultCoords.xy).xyz;
				return mix(color, reflectedColor, 1-screenEdgefactor);
		  	}
		  	
		  	//color = texture(environmentMap, normalize(normalize((inverse(viewMatrix) * vec4(reflectionVecView,0)).xyz))).rgb;
			return color;
		  	
		  }
	}
	
	return color;
}

/////////////////////

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	st /= secondPassScale;
  
	vec3 positionView = texture2D(positionMap, st).xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 color = texture2D(diffuseMap, st).xyz;
	float reflectiveness = texture2D(diffuseMap, st).w;
	
	// skip background
	if (positionView.z > -0.0001) {
	  discard;
	}
	vec3 normalView = texture2D(normalMap, st).xyz;
	
	float depth = texture2D(normalMap, st).w;
	vec4 specular = texture2D(specularMap, st);
	//vec4 finalColor = vec4(albedo,1) * ( vec4(phong(position.xyz, normalize(normal).xyz), 1));
	vec4 finalColor = phong(positionView, normalView, vec4(color,1), specular);
	
	/////////////////// SHADOWMAP
	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
  	float momentum2 = texture2D(shadowMap, positionShadow.xy).g;
  	float depthShadow = blurSample(shadowMap, positionShadow.xy, momentum2/20).r;

	visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow), 0, 1);

	finalColor *= visibility;
	//finalColor = vec4(visibility,visibility,visibility,visibility);
	/////////////////// SHADOWMAP
	
	float ao = 1;
	vec3 ssdo = vec3(0,0,0);
	if (useAmbientOcclusion && ambientOcclusionTotalStrength != 0) {
		vec3 N = normalView;
		float falloff = ambientOcclusionFalloff;
		const float samples = 8;
		const float invSamples = 1/samples;
		
		vec3 fres = normalize(rand(color.rg)*2) - vec3(1.0, 1.0, 1.0);
		vec3 ep = vec3(st, depth);
		float rad = ambientOcclusionRadius;
		float radD = rad*depth;
		float bl = 0.0f;
		float occluderDepth, depthDifference, normDiff;
		float totStrength = ambientOcclusionTotalStrength;
		float strength = ambientOcclusionStrength;
	
		for(int i=0; i<samples;++i) {
		  vec3 ray = (viewMatrix *vec4(radD*pSphere[i],0)).xyz;
		  vec3 se = ep + sign(dot(ray,N) )*ray;
		  vec4 occluderFragment = texture2D(normalMap,se.xy);
		  vec3 occNorm = occluderFragment.xyz;
		
		  // Wenn die Diff der beiden Punkte negativ ist, ist der Verdecker hinter dem aktuellen Bildpunkt
		  depthDifference = depth-occluderFragment.w;
		
		  // Berechne die Differenz der beiden Normalen als ein Gewicht
		  float dotProd = dot(occNorm,N);
		  normDiff = (1.0-dotProd);
		  
		  //////////
		  vec3 occluderColor = texture2D(diffuseMap, se.xy).xyz;
		  float dotProdSSDO = dot((viewMatrix * vec4(lightDirection, 0)).xyz, occNorm);
		  vec3 occluderLit = occluderColor * dotProdSSDO * lightDiffuse;
		  ssdo += occluderLit * dotProd;
		  //////////
		  
		  // the falloff equation, starts at falloff and is kind of 1/x^2 falling
		  bl += normDiff*step(falloff,depthDifference)*(1.0-smoothstep(falloff,strength,depthDifference));
	
	    }
	    
		ao = 1.0-totStrength*bl*invSamples;
		ssdo *= 1-ao;
		
	}
	
	
	//vec4 ambientTerm = vec4((ambientColor * ao), 0);
	out_DiffuseSpecular = finalColor;// + ambientTerm;
	if(reflectiveness == 0) {
		out_AOReflection = vec4(ao, out_DiffuseSpecular.rgb);
	} else {
		vec4 reflectedColor = vec4(rayCastReflect(out_DiffuseSpecular.xyz, st, positionView, normalView), 0);
		out_AOReflection = vec4(ao, reflectedColor.rgb);
	}
	//out_DiffuseSpecular = vec4(ssdo,1);
}
