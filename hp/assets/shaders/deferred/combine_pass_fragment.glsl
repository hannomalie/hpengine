#version 420

layout(binding=0) uniform sampler2D diffuseMap; // diffuse, metallic 
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D aoReflection; // ao, reflectedColor
layout(binding=3) uniform sampler2D motionMap; // motionVec
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth
layout(binding=6) uniform samplerCube globalEnvironmentMap; // normal, depth
layout(binding=7) uniform samplerCubeArray probes;
layout(binding=8) uniform sampler2D visibilityMap;


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
vec4 blurSample(sampler2D sampler, vec2 texCoord, float dist) { // TODO: MAKE THIS GAUSS..........

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

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000;
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
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

//return color;
//return probeColor;

	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = normalize(reflect(eyeToSurfaceView, targetNormalView));
	
	int STEPRAYLENGTH = 10;
	vec3 viewRay = STEPRAYLENGTH*normalize(reflectionVecView);
	
	vec3 currentViewPos = targetPosView;
	
	const int STEPS_1 = 20;
	const int STEPS_2 = 10;
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
				float mipMapLevelFromWorldDistance = clamp(distanceInWorld/10, 0, 5); // every 10 units, the next mipmap level is chosen
    			float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))*2), 0, 1);
    			//float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))-0.5)*2, 0, 1);
    			float mipMapChoser = roughness * 8;
    			mipMapChoser = max(mipMapChoser, mipMapLevelFromWorldDistance);
    			mipMapChoser = max(mipMapChoser, screenEdgefactor * 1);
    			vec3 reflectedColor =  textureLod(diffuseMap, resultCoords.xy, mipMapChoser).xyz;
    			//vec3 reflectedColor =  blurSample(diffuseMap, resultCoords.xy, 0.05).rgb;
    			//return vec3(screenEdgefactor, 0, 0);
    			
    			float screenEdgefactorX = clamp(abs(resultCoords.x) - 0.95, 0, 1);
    			float screenEdgefactorY = clamp(abs(resultCoords.y) - 0.95, 0, 1);
    			screenEdgefactor = 20*max(screenEdgefactorX, screenEdgefactorY);
    			//return vec3(screenEdgefactor, 0, 0);
    			
    			reflectedColor += reflectedColor * ambientColor;
    			reflectedColor += reflectedColor * textureLod(lightAccumulationMap, resultCoords.xy, 0).rgb; // since no specular and ambient termn, approximate it with a factor of two
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
	vec3 sum = rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView, roughness);
	const float factor = 0.025;
	vec3 offset0 = vec3(roughness,roughness,roughness) * factor;
	vec3 offset1 = vec3(roughness,roughness,-roughness) * factor;
	vec3 offset2 = vec3(roughness,-roughness,-roughness) * factor;
	vec3 offset3 = vec3(-roughness,-roughness,-roughness) * factor;
	vec3 offset4 = vec3(-roughness,-roughness,-roughness) * factor;
	
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset0, roughness);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset1, roughness);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset2, roughness);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset3, roughness);
	sum += rayCastReflect(color, probeColor, screenPos, targetPosView, targetNormalView+offset4, roughness);
	
	return sum/5;
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
	const float MAX_MIPMAPLEVEL = 10;
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

///////////////////////////////// HI-Z /////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////
const vec2 cb_screenSize = vec2(1280,720);
const vec2 hiZSize = vec2(1280,720)/1; // not sure if correct - this is mip level 0 size
const float rootLevel = fullScreenMipmapCount - 1;
const float HIZ_START_LEVEL = 6;
const float HIZ_STOP_LEVEL = 2;
const int MAX_ITERATIONS = 64;
const float HIZ_MAX_LEVEL = float(fullScreenMipmapCount);
const float HIZ_CROSS_EPSILON = 1*vec2(1/1280, 1/720); // mip level 0 texel size, TODO: Make not hardcoded

vec3 intersectDepthPlane(vec3 o, vec3 d, float t)
{
	return o + d * t;
}

vec2 getCell(vec2 ray, vec2 cellCount)
{
	return floor(ray * cellCount);
}

vec3 intersectCellBoundary(vec3 o, vec3 d, vec2 cellIndex, vec2 cellCount, vec2 crossStep, vec2 crossOffset)
{
	vec2 index = cellIndex + crossStep;
	index /= cellCount;
	index += crossOffset;
	vec2 delta = index - o.xy;
	delta /= d.xy;
	float t = min(delta.x, delta.y);
	return intersectDepthPlane(o, d, t);
}

float getMinimumDepthPlane(vec2 ray, float level, float rootLevel)
{
	//ray.y = 1-ray.y;
	return texture(visibilityMap, ray.xy, level).g;
	//return hiZBuffer.SampleLevel(sampPointClamp, ray.xy, level).r;
}

vec2 getCellCount(float level, float rootLevel)
{
	float div = level == 0.0f ? 1.0f : exp2(level);
	return cb_screenSize / vec2(div,div);
}

bool crossedCellBoundary(vec2 cellIdxOne, vec2 cellIdxTwo)
{
	return cellIdxOne.x != cellIdxTwo.x || cellIdxOne.y != cellIdxTwo.y;
}

vec3 highZTrace(vec3 p, vec3 v) {
	
	float level = HIZ_START_LEVEL;
	float iterations = 0.0f;
	
	// get the cell cross direction and a small offset to enter the next cell when doing cell crossing
	vec2 crossStep = vec2(v.x >= 0.0f ? 1.0f : -1.0f, v.y >= 0.0f ? 1.0f : -1.0f);
	vec2 crossOffset = (crossStep * HIZ_CROSS_EPSILON);
	crossStep.x = clamp(crossStep.x, 0, 1);
	crossStep.y = clamp(crossStep.y, 0, 1);
	
	// set current ray to original screen coordinate and depth
	vec3 ray = p.xyz;
	
	// scale vector such that z is 1.0f (maximum depth)
	vec3 d = v.xyz / v.z;
	
	// set starting point to the point where z equals 0.0f (minimum depth)
	vec3 o = intersectDepthPlane(p, d, -p.z);
	
	// cross to next cell to avoid immediate self-intersection
	vec2 rayCell = getCell(ray.xy, hiZSize.xy);
	ray = intersectCellBoundary(o, d, rayCell.xy, hiZSize.xy, crossStep.xy, crossOffset.xy);
	
	while(level >= HIZ_STOP_LEVEL && iterations < MAX_ITERATIONS)
	{
		// get the minimum depth plane in which the current ray resides
		float minZ = getMinimumDepthPlane(ray.xy, level, rootLevel);
		
		// get the cell number of the current ray
		const vec2 cellCount = getCellCount(level, rootLevel);
		const vec2 oldCellIdx = getCell(ray.xy, cellCount);

		// intersect only if ray depth is below the minimum depth plane
		vec3 tmpRay = intersectDepthPlane(o, d, max(ray.z, minZ));

		// get the new cell number as well
		const vec2 newCellIdx = getCell(tmpRay.xy, cellCount);

		// if the new cell number is different from the old cell number, a cell was crossed
		if(crossedCellBoundary(oldCellIdx, newCellIdx))
		{
			// intersect the boundary of that cell instead, and go up a level for taking a larger step next iteration
			tmpRay = intersectCellBoundary(o, d, oldCellIdx, cellCount.xy, crossStep.xy, crossOffset.xy);
			level = min(HIZ_MAX_LEVEL, level + 2.0f);
		}

		ray.xyz = tmpRay.xyz;

		// go down a level in the hi-z buffer
		--level;

		++iterations;
		
		//if(iterations == 7) { return (ray); }
	}
	
	float temp = iterations/64;
	return vec3(temp,temp,temp);
	return ray;
}

////////////////////////////////////////////////////////////////////////////////////////////////

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
  	//roughness = 0.0;
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
  	
  	vec4 motionVecProbeIndices = texture2D(motionMap, st); 
  	vec2 motionVec = motionVecProbeIndices.xy;
  	vec4 colorMetallic = texture2D(diffuseMap, st);
	//int probeIndex = getProbeIndexForPosition(positionWorld, normalWorld);
	int probeIndex = int(motionVecProbeIndices.b);
	probeIndex = probeIndex == 0 ? getProbeIndexForPosition(positionWorld, normalWorld) : probeIndex;
  	
  	float metallic = colorMetallic.a;
  	
	const float metalSpecularBoost = 1.4;
  	vec3 specularColor = mix(vec3(1,1,1), metalSpecularBoost*colorMetallic.rgb, metallic);
  	specularColor = mix(vec3(0.04,0.04,0.04), colorMetallic.rgb, metallic);
  	const float metalBias = 0.1;
  	vec3 color = mix(colorMetallic.xyz, vec3(0,0,0), clamp(metallic - metalBias, 0, 1));
  	
	vec4 lightDiffuseSpecular = texture(lightAccumulationMap, st);
	//lightDiffuseSpecular = textureLod(lightAccumulationMap, st, 4*length(lightDiffuseSpecular)/length(vec4(1,1,1,1)));
	float specularFactor = clamp(lightDiffuseSpecular.a, 0, 1);
	
	vec4 aoReflect = textureLod(aoReflection, st, 1);
	float ao = textureLod(aoReflection, st, 1).r;
	//float ao = blurSample(aoReflection, st, 0.0025).r;
	//ao += blurSample(aoReflection, st, 0.005).r;
	//ao /= 2;

	vec3 texCoords3d = normalize(reflect(V, normalWorld));
	texCoords3d = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	
	// Try to parallax-correct
	//texCoords3d -= texCoords3d * 0.0000001 * texture(getProbeForIndex(probeIndex), texCoords3d).a;
	//texCoords3d = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	
	vec3 reflectedColor = getProbeColor(positionWorld, V, normalWorld, roughness, st);
	vec3 environmentColor = reflectedColor;
	//reflectedColor = sslr(color, reflectedColor, st, positionView, normalView.rgb, roughness);
	//if(roughness > 0.1)
	{
		//reflectedColor = vec3(1,0,0);
		//reflectedColor = rayCastReflect(color, reflectedColor, st, positionView, normalView.rgb, roughness);
	}
	
	float reflectionMixer = (1-roughness); // the glossier, the more reflecting
	reflectionMixer -= (metallic); // metallic reflections should be tinted
	reflectionMixer = clamp(reflectionMixer, 0, 1);
	vec3 finalColor = mix(color, reflectedColor, reflectionMixer);
	vec3 specularTerm = 2*specularColor * specularFactor;
	vec3 diffuseTerm = 2*lightDiffuseSpecular.rgb*finalColor;
	
	vec3 ambientTerm = ambientColor * finalColor.rgb;// + 0.1* reflectedColor;
	vec3 normalBoxProjected = boxProjection(positionWorld, normalWorld, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	//float attenuation = 1-min(distance(positionWorld,environmentMapMin[probeIndex]), distance(positionWorld,environmentMapMax[probeIndex]))/(distance(environmentMapMin[probeIndex], environmentMapMax[probeIndex]/2));
	
	vec3 ambientDiffuse = vec3(0,0,0);
	vec3 ambientSpecular = vec3(0,0,0);
	vec4 ambientFromEnvironment = cookTorrance(-normalize(positionView), positionView, normalView.xyz, roughness, metallic, -normalWorld.xyz, environmentColor);
	ambientSpecular += clamp(ambientFromEnvironment.w, 0, 1) * getProbeColor(positionWorld, V, reflect(V, normalWorld), roughness, st);
	
	//ambientTerm = 0.5 * ambientColor * (finalColor.rgb * textureLod(getProbeForIndex(probeIndex), normalBoxProjected,9).rgb * max(dot(normalWorld, normalBoxProjected), 0.0) + textureLod(getProbeForIndex(probeIndex), normalBoxProjected,9).rgb*max(dot(reflect(V, normalWorld), -normalBoxProjected), 0.0));
	ambientTerm = ambientColor * finalColor * ambientFromEnvironment.xyz + ambientColor * specularColor * ambientSpecular;

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
	//out_color.rgb = color.xyz;
	//out_color.rgb = lightDiffuseSpecular.rgb;
	//out_color.rgb = vec3(motionVec,0);
	//out_color.rgb = specularTerm.rgb;
	//out_color.rgb = vec3(roughness,roughness,roughness);
	//out_color.rgb = specularTerm;
	//out_color.rgb = vec3(ao,ao,ao);
	//out_color.rgb = ambientFromEnvironment.rgb;
	
	/* vec2 positionScreenSpaceXY = st;
	vec3 positionScreenSpace = vec3(positionScreenSpaceXY, (normalView.w));
	
	vec3 reflectionWorldSpace = normalize(reflect(normalize(V), normalize(normalWorld)));
	//reflectionWorldSpace = vec3(0,1,0);
	vec3 secondPointWorldSpace = positionWorld + reflectionWorldSpace;
	vec4 secondPointScreenSpace = (projectionMatrix * viewMatrix * vec4(secondPointWorldSpace,1));
	secondPointScreenSpace /= secondPointScreenSpace.w;
	// secondPoint now in normalized screen device coordinates
	//secondPointScreenSpace.xy *= vec2(0.5, -0.5);
	secondPointScreenSpace.xy *= vec2(0.5, 0.5);
	secondPointScreenSpace.xy += vec2(0.5, 0.5);
	
	vec3 reflectionVectorScreenSpace = secondPointScreenSpace.xyz - positionScreenSpace;
	
	if(st.x < 0.5) {
		out_color.rgb = highZTrace(positionScreenSpace, reflectionVectorScreenSpace.xyz).xyz; // highz result used as uv into the colorbuffer is like cone tracing perfect reflection
		//out_color.g = 1-out_color.g;
		//out_color.rgb = textureLod(diffuseMap, (out_color.xy), 0).rgb;
	}*/
	
	/*if(st.x < 0.5) {
		float temp = 4*linearizeDepth(textureLod(visibilityMap, st, 2).b);
		out_color.rgb = vec3(temp,temp,temp);
	} else  {
		float temp = 4*linearizeDepth(textureLod(visibilityMap, st, 3).b);
		out_color.rgb = vec3(temp,temp,temp);
	}*/
	/* else {
		float temp = (textureLod(visibilityMap, st, 2).r);
		out_color.rgb = vec3(temp,temp,temp);
	}*/
	
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
