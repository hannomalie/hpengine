#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform sampler2D lightAccumulationMap; // diffuse, specular

layout(binding=6) uniform samplerCube globalEnvironmentMap;

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
uniform mat4 projectionMatrix;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform bool useAmbientOcclusion = true;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[192];
uniform vec3 environmentMapMax[192];

in vec2 pass_TextureCoord;

out vec4 out_diffuseEnvironment;
out vec4 out_specularEnvironment;

float rand(vec2 co){
	return 0.5+(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453))*0.5;
}

float getAmbientOcclusion(vec2 st) {
	
	float ao = 1;
	vec3 ssdo = vec3(0,0,0);
	
	float sum = 0.0;
	float prof = texture(normalMap, st.xy).w;
	vec3 norm = normalize(vec3(texture(normalMap, st.xy).xyz)); //*2.0-vec3(1.0)
	const int NUM_SAMPLES = 4;
	int hf = NUM_SAMPLES/2;
	
	//calculate sampling rates:
	float ratex = (2.0/1280.0);
	float ratey = (2.0/720.0);
	float incx = ratex*30;//gi radius
	float incy = ratey*30;
	float incx2 = ratex*8;//ao radius
	float incy2 = ratey*8;
	if (useAmbientOcclusion) {
		for(int i=-hf; i < hf; i++) {
		      for(int j=-hf; j < hf; j++) {
		 
		      if (i != 0 || j!= 0) {
	 
			      vec2 coords = vec2(i*incx,j*incy)/prof;
			      vec2 coords2 = vec2(i*incx2,j*incy2)/prof;
			
			      float prof2 = texture2D(normalMap,st.xy+coords*rand(st.xy)).w;
			      float prof2g = texture2D(normalMap,st.xy+coords2*rand(st.xy)).w;
			      vec3 norm2g = normalize(vec3(texture2D(normalMap,st.xy+coords2*rand(st.xy)).xyz)); //*2.0-vec3(1.0)
			      vec3 dcolor2 = texture2D(diffuseMap, st.xy+coords*rand(st.xy)).rgb;
			
			      //OCCLUSION:
			      //calculate approximate pixel distance:
			      vec3 dist2 = vec3(coords2,prof-prof2g);
			      //calculate normal and sampling direction coherence:
			      float coherence2 = dot(normalize(-coords2),normalize(vec2(norm2g.xy)));
			      //if there is coherence, calculate occlusion:
			      if (coherence2 > 0){
			          float pformfactor2 = 0.5*((1.0-dot(norm,norm2g)))/(3.1416*pow(abs(length(dist2*2)),2.0)+0.5);//el 4: depthscale
			          sum += clamp(pformfactor2*0.2,0.0,1.0);//ao intensity; 
			      }
		      }
		   }
		}
	}
	
	ao = clamp(1.0-(sum/NUM_SAMPLES),0,1);
	return ao;
}

samplerCube getProbeForIndex(int probeIndex) {
	if(probeIndex == 191) { return probe191; }
	else if(probeIndex == 190) { return probe190; }
	else if(probeIndex == 189) { return probe189; }
	else if(probeIndex == 188) { return probe188; }
	else if(probeIndex == 187) { return probe187; }
	else if(probeIndex == 186) { return probe186; }
	else if(probeIndex == 185) { return probe185; }
	else if(probeIndex == 184) { return probe184; }
	else if(probeIndex == 183) { return probe183; }
	else if(probeIndex == 182) { return probe182; }
	else if(probeIndex == 181) { return probe181; }
	else if(probeIndex == 180) { return probe180; }
	else if(probeIndex == 179) { return probe179; }
	else if(probeIndex == 178) { return probe178; }
	else if(probeIndex == 177) { return probe177; }
	else if(probeIndex == 176) { return probe176; }
	else if(probeIndex == 175) { return probe175; }
	else if(probeIndex == 174) { return probe174; }
	else if(probeIndex == 173) { return probe173; }
	else if(probeIndex == 172) { return probe172; }
	else if(probeIndex == 171) { return probe171; }
	else { return globalEnvironmentMap; }
}
vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 rayCastReflect(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness) {
if(dot(normalize(targetNormalView), normalize(targetPosView))>0) {
	return probeColor;
}
//return color;
//return probeColor;

	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = normalize(reflect(eyeToSurfaceView, targetNormalView));
	
	int STEPRAYLENGTH = 5;
	vec3 viewRay = STEPRAYLENGTH*normalize(reflectionVecView);
	
	vec3 currentViewPos = targetPosView;
	
	const int STEPS_1 = 60;
	const int STEPS_2 = 30;
	for (int i = 0; i < STEPS_1; i++) {
	
		  currentViewPos += viewRay;
		  
		  vec3 currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
		  float difference = currentViewPos.z - currentPosSample.z;
		  if (difference < 0) {
		  	
		  	currentViewPos -= viewRay;
		  	
		  	for(int x = 0; x < STEPS_2; x++) {
		 		currentViewPos += viewRay/STEPS_2;
		  		currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
				  float absDifference = distance(currentViewPos.z, currentPosSample.z);
				  if (absDifference < 0.01) {
  		  		  	break;
				  }
		  	}
		  	
  		  	vec4 resultCoords = getViewPosInTextureSpace(currentPosSample);
  			//if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1)
			{
				float distanceInWorld = distance(currentPosSample, targetPosView);
				float distanceInWorldPercent = distanceInWorld / 50;
    			float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))*2), 0, 1);
    			//float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))-0.5)*2, 0, 1);
    			float mipMapChoser = roughness * 9;
    			mipMapChoser = max(mipMapChoser, screenEdgefactor * 3);
    			
    			
    			float screenEdgefactorX = clamp(abs(resultCoords.x) - 0.95, 0, 1);
    			float screenEdgefactorY = clamp(abs(resultCoords.y) - 0.95, 0, 1);
    			screenEdgefactor = 20*max(screenEdgefactorX, screenEdgefactorY);
    			//return vec3(screenEdgefactor, 0, 0);
    			
    			vec4 diffuseColorMetallic = textureLod(diffuseMap, resultCoords.xy, mipMapChoser);
    			vec3 diffuseColor = diffuseColorMetallic.xyz;
				const float metalSpecularBoost = 1.4;
    			vec3 specularColor = mix(vec3(0.04,0.04,0.04), metalSpecularBoost*diffuseColorMetallic.rgb, diffuseColorMetallic.a);
			  	const float metalBias = 0.1;
			  	diffuseColor = mix(diffuseColorMetallic.xyz, vec3(0,0,0), clamp(diffuseColorMetallic.a - metalBias, 0, 1));
    			
    			vec4 lightDiffuseSpecular = textureLod(lightAccumulationMap, resultCoords.xy, 0);
    			vec3 reflectedColor = diffuseColor.rgb + diffuseColor.rgb * lightDiffuseSpecular.rgb; // since no specular and ambient termn, approximate it with a factor of two
    			reflectedColor += clamp(lightDiffuseSpecular.a, 0, 1) * specularColor;
				return mix(probeColor, reflectedColor, 1-screenEdgefactor);
		  	}
		  	//return vec3(1,0,0);
		  	//color = texture(environmentMap, normalize(normalize((inverse(viewMatrix) * vec4(reflectionVecView,0)).xyz))).rgb;
			return probeColor;
		  }
	}
	
	return probeColor;
}

vec3 getIntersectionPoint(vec3 position_world, vec3 texCoords3d, vec3 environmentMapMin, vec3 environmentMapMax) {
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
	
	return posonbox; 
}

vec3 boxProjection(vec3 position_world, vec3 texCoords3d, vec3 environmentMapMin, vec3 environmentMapMax) {
	vec3 posonbox = getIntersectionPoint(position_world, texCoords3d, environmentMapMin, environmentMapMax);
	
	//texCoords3d = normalize(posonbox - vec3(0,0,0));
	vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}
bool isInside(vec3 position, vec3 minPosition, vec3 maxPosition) {
	return(all(greaterThanEqual(position, minPosition)) && all(lessThanEqual(position, maxPosition))); 
	//return(position.x >= minPosition.x && position.y >= minPosition.y && position.z >= minPosition.z && position.x <= maxPosition.x && position.y <= maxPosition.y && position.z <= maxPosition.z);
}

int getProbeIndexForPosition(vec3 position, vec3 normal) {
	
	vec3 currentEnvironmentMapMin = environmentMapMin[0];
	vec3 currentEnvironmentMapMax = environmentMapMax[0];
	vec3 currentIntersectionPoint = getIntersectionPoint(position, normal, currentEnvironmentMapMin, currentEnvironmentMapMax);
	float minDist = distance(currentIntersectionPoint, position);
	int iForNearest = 191;
	
	for(int i = 0; i < activeProbeCount; i++) {
		currentEnvironmentMapMin = environmentMapMin[191- i];
		currentEnvironmentMapMax = environmentMapMax[191 -i];
		currentIntersectionPoint = getIntersectionPoint(position, normal, currentEnvironmentMapMin, currentEnvironmentMapMax);
		if(!isInside(position, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
		
		float currentDist = distance(currentIntersectionPoint, position);
		if(currentDist < minDist) {
			minDist = currentDist;
			iForNearest = i;
		}
	}
	
	return 191-iForNearest;
}

vec4[2] getTwoProbeIndicesForPosition(vec3 position, vec3 normal, vec2 uv) {
	vec4[2] result;
	
	// Only if two indices can be precalculated, we skip the intersection test. one index is not enough to avoid flickering on moving objects
	vec2 precalculatedIndices = texture(motionMap, uv).ba;
	if(precalculatedIndices.x != 0) {
		vec3 mini = environmentMapMin[int(precalculatedIndices.x)];
		vec3 maxi = environmentMapMax[int(precalculatedIndices.x)];
		result[0] = vec4(getIntersectionPoint(position, normal, mini, maxi), precalculatedIndices.x);
		if(precalculatedIndices.y != 0) {
			result[1] = vec4(getIntersectionPoint(position, normal, environmentMapMin[int(precalculatedIndices.y)], environmentMapMax[int(precalculatedIndices.y)]), precalculatedIndices.y);
			return result;
		}
	}
	
	vec3 currentEnvironmentMapMin1 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax1 = environmentMapMax[0];
	vec3 currentEnvironmentMapMin2 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax2 = environmentMapMax[0];
	vec3 intersectionPoint1 = getIntersectionPoint(position, normal, currentEnvironmentMapMin1, currentEnvironmentMapMax1);
	vec3 intersectionPoint2 = intersectionPoint1;
	float minDist1 = distance(intersectionPoint1, position);
	int iForNearest1 = 191;
	float minDist2 = minDist1;
	int iForNearest2 = 191;
	///////////
	//result[0] = vec4(intersectionPoint1, 191-iForNearest1);
	//result[1] = vec4(intersectionPoint2, 191-iForNearest2);
	//return result;
	///////////
	
	for(int i = 0; i < activeProbeCount; i++) {
		vec3 currentEnvironmentMapMin = environmentMapMin[191- i];
		vec3 currentEnvironmentMapMax = environmentMapMax[191 -i];
		if(!isInside(position, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
		vec3 currentIntersectionPoint = getIntersectionPoint(position, normal, currentEnvironmentMapMin, currentEnvironmentMapMax);
		
		float currentDist = distance(currentIntersectionPoint, position);
		if(currentDist < minDist1) {
			minDist2 = minDist1;
			iForNearest2 = iForNearest1;
			intersectionPoint2 = intersectionPoint1;
			
			minDist1 = currentDist;
			iForNearest1 = i;
			intersectionPoint1 = currentIntersectionPoint;
			
		} else if(currentDist < minDist2) {
			minDist2 = currentDist;
			iForNearest2 = i;
			intersectionPoint2 = currentIntersectionPoint;
			
		}
	}
	
	result[0] = vec4(intersectionPoint1, 191-iForNearest1);
	result[1] = vec4(intersectionPoint2, 191-iForNearest2);
	return result;
}

vec4[2] getTwoNearestProbeIndicesAndIntersectionsForPosition(vec3 position, vec3 normal) {
	
	vec4[2] result;
	vec3 currentEnvironmentMapMin1 = environmentMapMin[191];
	vec3 currentEnvironmentMapMax1 = environmentMapMax[191];
	vec3 currentEnvironmentMapMin2 = environmentMapMin[191];
	vec3 currentEnvironmentMapMax2 = environmentMapMax[191];
	vec3 currentCenter1 = currentEnvironmentMapMin1 + distance(currentEnvironmentMapMin1, currentEnvironmentMapMax1)/2;
	vec3 currentCenter2 = currentEnvironmentMapMin1 + distance(currentEnvironmentMapMin1, currentEnvironmentMapMax1)/2;
	float minDist1 = distance(currentCenter1, position);
	int iForNearest1 = 191;
	float minDist2 = minDist1;
	int iForNearest2 = 191;
	
	if(!isInside(position, currentEnvironmentMapMin1, currentEnvironmentMapMax1)) { minDist1 = 10000;minDist2 = 10000; }
	
	for(int i = 0; i < activeProbeCount; i++) {
		vec3 currentEnvironmentMapMin = environmentMapMin[191- i];
		vec3 currentEnvironmentMapMax = environmentMapMax[191 -i];
		if(!isInside(position, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
		vec3 currentCenter = currentEnvironmentMapMin + distance(currentEnvironmentMapMin, currentEnvironmentMapMax)/2;
		
		float currentDist = distance(currentCenter, position);
		if(currentDist < minDist1) {
			minDist2 = minDist1;
			iForNearest2 = iForNearest1;
			
			minDist1 = currentDist;
			iForNearest1 = i;
		} else if(currentDist < minDist2){
			minDist2 = currentDist;
			iForNearest2 = i;
		}
	}
	
	result[0] = vec4(getIntersectionPoint(position, normal, currentEnvironmentMapMin1, currentEnvironmentMapMax1), 191-iForNearest1);
	result[1] = vec4(getIntersectionPoint(position, normal, currentEnvironmentMapMin2, currentEnvironmentMapMax2), 191-iForNearest2);
	return result;
}

float calculateWeight(vec3 positionWorld, vec3 minimum, vec3 maximum) {
	vec3 halfSize = (maximum - minimum)/2;
	vec3 halfSizeInner = 0.8 * halfSize;
	
	vec3 center = minimum + halfSize;
	vec3 positionInBoxSpace = positionWorld - center;
	positionInBoxSpace = vec3(abs(positionInBoxSpace.x), abs(positionInBoxSpace.y), abs(positionInBoxSpace.z)); // work in the positive quarter of the cube
	
	//vec3 overhead = (positionInBoxSpace - halfSizeInner) / (halfSize - halfSizeInner);
	vec3 overhead = (positionInBoxSpace - halfSizeInner);
	overhead.x = clamp(overhead.x, 0, overhead.x);
	overhead.y = clamp(overhead.y, 0, overhead.y);
	overhead.z = clamp(overhead.z, 0, overhead.z);
	overhead /= (halfSize - halfSizeInner);
	
	return max(overhead.x, max(overhead.y, overhead.z));
}
const bool NO_INTERPOLATION_IF_ONE_PROBE_GIVEN = true;

vec3 getProbeColor(vec3 positionWorld, vec3 V, vec3 normalWorld, float roughness, vec2 uv) {
	const float MAX_MIPMAPLEVEL = 9;
	float mipMapLevel = roughness * MAX_MIPMAPLEVEL;
	float mipMapLevelSecond = mipMapLevel;
	
	vec4[2] twoIntersectionsAndIndices = getTwoProbeIndicesForPosition(positionWorld, normalWorld, uv);
	//vec4[2] twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForPosition(positionWorld, normalWorld);
	int probeIndexNearest = int(twoIntersectionsAndIndices[0].w);
	int probeIndexSecondNearest = int(twoIntersectionsAndIndices[1].w);
	
	if(probeIndexNearest == 0) {
		return texture(globalEnvironmentMap, normalWorld, mipMapLevel).rgb;
	} else if(probeIndexSecondNearest == 0) {
		vec3 t3d = normalize(reflect(V, normalWorld));
		vec3 t3d_bp = boxProjection(positionWorld, t3d, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
		vec3 c = textureLod(getProbeForIndex(probeIndexNearest), t3d_bp, mipMapLevel).rgb;
		if(NO_INTERPOLATION_IF_ONE_PROBE_GIVEN) {
			return c;
		}
	}
	
	vec3 intersectionNearest = twoIntersectionsAndIndices[0].xyz;
	vec3 intersectionSecondNearest = twoIntersectionsAndIndices[1].xyz;
	
	float mixer = calculateWeight(positionWorld, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
	//return vec3(mixer, mixer, mixer);
	
	vec3 texCoords3d = normalize(reflect(V, normalWorld));
	vec3 boxProjectedNearest = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
	vec3 boxProjectedSecondNearest = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndexSecondNearest], environmentMapMax[probeIndexSecondNearest]);
	
	mipMapLevel *= clamp(distance(positionWorld, boxProjectedNearest)/50, 0, 1);
	mipMapLevelSecond *= clamp(distance(positionWorld, boxProjectedSecondNearest)/50, 0, 1);
	vec3 colorNearest = textureLod(getProbeForIndex(probeIndexNearest), boxProjectedNearest, mipMapLevel).rgb;
	vec3 colorSecondNearest = textureLod(getProbeForIndex(probeIndexSecondNearest), boxProjectedSecondNearest, mipMapLevelSecond).rgb;
	
	float distanceToNearestIntersection = distance(positionWorld, twoIntersectionsAndIndices[0].xyz);
	float distanceToSecondNearestIntersection = distance(positionWorld, twoIntersectionsAndIndices[1].xyz);
	
	return mix(colorNearest, colorSecondNearest, mixer);
}

void main()
{
	vec2 st = pass_TextureCoord;
	vec4 positionViewRoughness = textureLod(positionMap, st, 0);
	vec3 positionView = positionViewRoughness.rgb;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 normalView = textureLod(normalMap, st, 0).rgb;
  	vec3 normalWorld = (inverse(viewMatrix) * vec4(normalView,0)).xyz;
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	vec3 color = textureLod(diffuseMap, st, 0).rgb;
	float roughness = positionViewRoughness.a;
  	//roughness = 0.0;
	
	out_diffuseEnvironment.rgb = getProbeColor(positionWorld, V, normalWorld, roughness, st);
	out_diffuseEnvironment.a = getAmbientOcclusion(st);
	//out_diffuseEnvironment.rgb = rayCastReflect(color, out_diffuseEnvironment.rgb, st, positionView, normalView.rgb, roughness);
	out_specularEnvironment.rgb = getProbeColor(positionWorld, V, reflect(V, normalWorld), roughness, st);
}