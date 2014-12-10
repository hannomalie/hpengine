#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform sampler2D lightAccumulationMap; // diffuse, specular

layout(binding=6) uniform samplerCube globalEnvironmentMap;
layout(binding=7) uniform samplerCubeArray probes;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform bool useAmbientOcclusion = true;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

in vec2 pass_TextureCoord;

out vec4 out_diffuseEnvironment;
out vec4 out_specularEnvironment;

float rand(vec2 co){
	return 0.5+(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453))*0.5;
}

mat3 createOrthonormalBasis(vec3 n) {

	vec3 x;
    vec3 z;

    if(abs(n.x) < abs(n.y)){
        if(abs(n.x) < abs(n.z)){
            x = vec3(1.0f,0.0f,0.0f);
        } else {
            x = vec3(0.0f,0.0f,1.0f);
        }
    } else {
        if(abs(n.y) < abs(n.z)){
            x = vec3(0.0f,1.0f,0.0f);
        }else{
            x = vec3(0.0f,0.0f,1.0f);
        }
    }

    z = normalize(cross(x,n));
    x = cross(n,z);

    mat3 m = mat3(  x.x, n.x, z.x,
                    x.y, n.y, z.y,
                    x.z, n.z, z.z);
    
    return m;
}

// https://www.opengl.org/discussion_boards/showthread.php/164815-Taking-multiple-samples-in-cubemap-shadowmaps
vec4 sampleCubeMap(int index, vec3 n, float roughness) {
	const int NUM_SAMPLES = 10;
	
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
	
	mat3 basis = createOrthonormalBasis(n);
	
	vec4 result = textureLod(probes, vec4(n, index), 0);

	float glossiness = (1-roughness);
	float roughnessMapping = 6 +  glossiness * glossiness * 100;
	for(int i = 0; i < NUM_SAMPLES; i++) {
		vec2 sample_2d = poissonDisk[i]/roughnessMapping;
		//vec2 sample_2d = poissonDisk[i];
		vec3 sample_3d = vec3(sample_2d.x, 1 - sqrt(length(sample_2d)), sample_2d.y);
		vec3 transformed_sample = inverse(basis) * sample_3d;
		result += textureLod(probes, vec4(normalize(transformed_sample), index), 0);// * clamp(dot(sample_3d, -n),0,1);
	}
	
	return result / (NUM_SAMPLES + 1);
}
float getAmbientOcclusion(vec2 st) {
	
	float ao = 1;
	vec3 ssdo = vec3(0,0,0);
	
	float sum = 0.0;
	float prof = texture(normalMap, st.xy).w; // depth
	vec3 norm = normalize(vec3(texture(normalMap, st.xy).xyz)); //*2.0-vec3(1.0)
	const int NUM_SAMPLES = 4;
	int hf = NUM_SAMPLES/2;
	
	//calculate sampling rates:
	float ratex = (1.0/1280.0);
	float ratey = (1.0/720.0);
	float incx2 = ratex*8;//ao radius
	float incy2 = ratey*8;
	if (useAmbientOcclusion) {
		for(int i=-hf; i < hf; i++) {
		      for(int j=-hf; j < hf; j++) {
		 
		      if (i != 0 || j!= 0) {
	 
			      vec2 coords2 = vec2(i*incx2,j*incy2)/prof;
			
			      float prof2g = texture2D(normalMap,st.xy+coords2*rand(st.xy)).w; // depth
			      vec3 norm2g = normalize(vec3(texture2D(normalMap,st.xy+coords2*rand(st.xy)).xyz)); //*2.0-vec3(1.0)
			
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
	
	int STEPRAYLENGTH = 8;
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
  			if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1)
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
			  	vec3 ambientDiffuseColor = diffuseColor;
			  	diffuseColor = mix(diffuseColorMetallic.xyz, vec3(0,0,0), clamp(diffuseColorMetallic.a - metalBias, 0, 1));
    			
    			vec4 lightDiffuseSpecular = textureLod(lightAccumulationMap, resultCoords.xy, mipMapChoser-2);
    			vec3 reflectedColor = (0.4 * ambientColor) * ambientDiffuseColor + diffuseColor.rgb * lightDiffuseSpecular.rgb; // since no specular and ambient termn, approximate it with a factor of two
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

vec4[2] getTwoNearestProbeIndicesAndIntersectionsForIntersection(vec3 position, vec3 normal, vec2 uv) {
	vec4[2] result;
	
	// Only if two indices can be precalculated, we skip the intersection test. one index is not enough to avoid flickering on moving objects
	vec2 precalculatedIndices = texture(motionMap, uv).ba;
	if(precalculatedIndices.x != -1) {
		vec3 mini = environmentMapMin[int(precalculatedIndices.x)];
		vec3 maxi = environmentMapMax[int(precalculatedIndices.x)];
		result[0] = vec4(getIntersectionPoint(position, normal, mini, maxi), precalculatedIndices.x);
		if(precalculatedIndices.y != -1) {
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
	float minDist1 = 10000;
	int iForNearest1 = -1;
	float minDist2 = minDist1;
	int iForNearest2 = -1;
	
	for(int i = 0; i < activeProbeCount; i++) {
		vec3 currentEnvironmentMapMin = environmentMapMin[i];
		vec3 currentEnvironmentMapMax = environmentMapMax[i];
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
	
	result[0] = vec4(intersectionPoint1, iForNearest1);
	result[1] = vec4(intersectionPoint2, iForNearest2);
	return result;
}

vec4[2] getTwoNearestProbeIndicesAndIntersectionsForPosition(vec3 position, vec3 normal, vec2 uv) {
	vec4[2] result;
	
	ivec2 precalculatedIndices = ivec2(texture(motionMap, uv).ba);
	if(precalculatedIndices.x != -1) {
		vec3 mini = environmentMapMin[precalculatedIndices.x];
		vec3 maxi = environmentMapMax[precalculatedIndices.x];
		result[0] = vec4(getIntersectionPoint(position, normal, mini, maxi), precalculatedIndices.x);

		if(precalculatedIndices.y != -1) {
			result[1] = vec4(getIntersectionPoint(position, normal, environmentMapMin[int(precalculatedIndices.y)], environmentMapMax[int(precalculatedIndices.y)]), precalculatedIndices.y);
			return result;
		}
	}
	
	vec3 currentEnvironmentMapMin1 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax1 = environmentMapMax[0];
	vec3 currentEnvironmentMapMin2 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax2 = environmentMapMax[0];
	vec3 currentCenter1 = currentEnvironmentMapMin1 + distance(currentEnvironmentMapMin1, currentEnvironmentMapMax1)/2;
	vec3 currentCenter2 = currentCenter1;
	float minDist1 = 10000;
	int iForNearest1 = -1;
	float minDist2 = minDist1;
	int iForNearest2 = -1;
	
	for(int i = 0; i < activeProbeCount; i++) {
		vec3 currentEnvironmentMapMin = environmentMapMin[i];
		vec3 currentEnvironmentMapMax = environmentMapMax[i];
		if(!isInside(position, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
		vec3 distVectorHalf = vec3(distance(currentEnvironmentMapMin.x, currentEnvironmentMapMax.x), distance(currentEnvironmentMapMin.y, currentEnvironmentMapMax.y), distance(currentEnvironmentMapMin.z, currentEnvironmentMapMax.z))/2;
		vec3 currentCenter = currentEnvironmentMapMin + distVectorHalf;
		
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
	
	result[0] = vec4(getIntersectionPoint(position, normal, currentEnvironmentMapMin1, currentEnvironmentMapMax1), iForNearest1);
	result[1] = vec4(getIntersectionPoint(position, normal, currentEnvironmentMapMin2, currentEnvironmentMapMax2), iForNearest2);
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
const bool MULTIPLE_SAMPLES = false;

vec3 getProbeColor(vec3 positionWorld, vec3 V, vec3 normalWorld, float roughness, vec2 uv) {
	const float MAX_MIPMAPLEVEL = 11;
	float mipMapLevel = roughness * MAX_MIPMAPLEVEL;
	float mipMapLevelSecond = mipMapLevel;
	
	vec4[2] twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForPosition(positionWorld, normalWorld, uv);
	//vec4[2] twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForIntersection(positionWorld, normalWorld, uv);
	int probeIndexNearest = int(twoIntersectionsAndIndices[0].w);
	int probeIndexSecondNearest = int(twoIntersectionsAndIndices[1].w);
	
	if(probeIndexNearest == -1) {
		vec4 c = texture(globalEnvironmentMap, normalWorld, mipMapLevel).rgba;
		return c.rgb;
	} else if(probeIndexSecondNearest == -1) {
		vec3 t3d = normalize(normalWorld);
		vec3 t3d_bp = boxProjection(positionWorld, t3d, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
		vec4 c = textureLod(probes, vec4(t3d_bp, probeIndexNearest), mipMapLevel);
		if(MULTIPLE_SAMPLES) {
			c = sampleCubeMap(probeIndexNearest, t3d_bp, roughness);
		}
		if (NO_INTERPOLATION_IF_ONE_PROBE_GIVEN) {
			if (c.a == 0) {
				return c.rgb;
				return textureLod(globalEnvironmentMap, normalWorld, mipMapLevel).rgb;
			} else {
				return c.rgb;
			}
		}
	}
	vec3 intersectionNearest = twoIntersectionsAndIndices[0].xyz;
	vec3 intersectionSecondNearest = twoIntersectionsAndIndices[1].xyz;
	
	float mixer = calculateWeight(positionWorld, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
	
	vec3 texCoords3d = normalize(normalWorld);//normalize(reflect(V, normalWorld));
	vec3 boxProjectedNearest = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest]);
	vec3 boxProjectedSecondNearest = boxProjection(positionWorld, texCoords3d, environmentMapMin[probeIndexSecondNearest], environmentMapMax[probeIndexSecondNearest]);
	
	mipMapLevel *= clamp(distance(positionWorld, boxProjectedNearest)/50, 0, 1);
	mipMapLevelSecond *= clamp(distance(positionWorld, boxProjectedSecondNearest)/50, 0, 1);
	vec4 colorVisibilityNearest = textureLod(probes, vec4(boxProjectedNearest, probeIndexNearest), mipMapLevel);
	if(MULTIPLE_SAMPLES) {
		colorVisibilityNearest = sampleCubeMap(probeIndexNearest, boxProjectedNearest, roughness);
	}
	vec3 colorNearest = colorVisibilityNearest.rgb;
	
	vec4 colorVisibilitySecondNearest = textureLod(probes, vec4(boxProjectedSecondNearest, probeIndexSecondNearest), mipMapLevelSecond);
	if(MULTIPLE_SAMPLES) {
		colorVisibilitySecondNearest = sampleCubeMap(probeIndexSecondNearest, boxProjectedSecondNearest, roughness);
	}
	vec3 colorSecondNearest = colorVisibilitySecondNearest.rgb;

	mixer = max(mixer, 1-colorVisibilityNearest.a);
	float distanceToNearestIntersection = distance(positionWorld, twoIntersectionsAndIndices[0].xyz);
	float distanceToSecondNearestIntersection = distance(positionWorld, twoIntersectionsAndIndices[1].xyz);
	
	//return mix(mix(colorNearest, colorSecondNearest, mixer), textureLod(globalEnvironmentMap, normalWorld, mipMapLevel).rgb, 1-colorVisibilitySecondNearest.a);
	return mix(colorNearest, colorSecondNearest, mixer);
}

void main()
{
	vec2 st = pass_TextureCoord;
	vec4 positionViewRoughness = textureLod(positionMap, st, 0);
	vec3 positionView = positionViewRoughness.rgb;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 normalView = textureLod(normalMap, st, 0).rgb;
  	vec3 normalWorld = normalize(inverse(viewMatrix) * vec4(normalView,0)).xyz;
	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	vec3 color = textureLod(diffuseMap, st, 0).rgb;
	float roughness = positionViewRoughness.a;
	//roughness = 0;
	
	out_diffuseEnvironment.rgb = getProbeColor(positionWorld, V, normalWorld, roughness, st);
	out_diffuseEnvironment.a = getAmbientOcclusion(st);
	out_specularEnvironment.rgb = getProbeColor(positionWorld, V, normalize(reflect(V, normalWorld)), roughness, st);
	out_specularEnvironment.rgb = rayCastReflect(color, out_specularEnvironment.rgb, st, positionView, normalView.rgb, roughness);
}