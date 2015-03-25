#define WORK_GROUP_SIZE 16

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding = 0, rgba16f) uniform image2D out0;
layout(binding = 1, rgba16f) uniform image2D out1;
layout(binding = 2, rgba16f) uniform image2D out2;
layout(binding = 3, rgba16f) uniform image2D out3;
layout(binding = 4, rgba16f) uniform image2D out4;
layout(binding = 5, rgba16f) uniform image2D out5;
layout(binding = 6, rgba16f) uniform image2D out6;
layout(binding = 7, rgba16f) uniform image2D out7;

layout(binding = 8) uniform samplerCubeArray probes;

uniform float screenWidth;
uniform float screenHeight; 
uniform int currentProbe;
uniform int currentCubemapSide;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

const float PI = 3.1415926536;
const float MAX_MIPMAPLEVEL = 8; // HEMISPHERE is half the cubemap

struct ProbeSample {
	vec3 diffuseColor;
	vec3 specularColor;
	vec3 refractedColor;
};

vec3 getNormalForTexel(vec3 positionWorld, vec2 texelPosition) {
	texelPosition -= 0.5; // remap 0-1 to -0.5-0.5
	texelPosition *= 2; // remap to -1-1
	vec3 normal;
	
	if(currentCubemapSide == 0) { // facing x-dir
		normal = vec3(1, -texelPosition.y, -texelPosition.x);
	} else if(currentCubemapSide == 1) { // facing -x-dir
		normal = vec3(-1, -texelPosition.y, texelPosition.x);
	} else if(currentCubemapSide == 2) { // facing y-dir
		normal = vec3(-texelPosition.x, 1, -texelPosition.y);
	} else if(currentCubemapSide == 3) { // facing -y-dir
		normal = vec3(texelPosition.x, -1, -texelPosition.y);
	} else if(currentCubemapSide == 4) { // facing z-dir
		normal = vec3(texelPosition.x, -texelPosition.y, 1);
	} else if(currentCubemapSide == 5) { // facing -z-dir
		normal = vec3(-texelPosition.x, -texelPosition.y, -1);
	}
	
	return normalize(normal);
}

struct BoxIntersectionResult {
	int indexNearest;
	int indexSecondNearest;
	
	vec3 intersectionNormalNearest;
	vec3 intersectionReflectedNearest;
	
	vec3 intersectionNormalSecondNearest;
	vec3 intersectionReflectedSecondNearest;
};

struct TileProbes {
	int count;
	int[10] indices;
};

vec3 findMainAxis(vec3 input) {
	if(abs(input.x) > abs(input.z)) {
		return vec3(1,0,0);
	} else {
		return vec3(0,0,1);
	}
}
bool isInside(vec3 position, vec3 minPosition, vec3 maxPosition) {
	return(all(greaterThanEqual(position, minPosition)) && all(lessThanEqual(position, maxPosition))); 
}

vec2 cartesianToSpherical(vec3 cartCoords){
	float a = atan(cartCoords.y/cartCoords.x);
	float b = atan(sqrt(cartCoords.x*cartCoords.x+cartCoords.y*cartCoords.y))/cartCoords.z;
	return vec2(a, b);
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

// http://blog.tobias-franke.eu/2014/03/30/notes_on_importance_sampling.html
float p(vec2 spherical_coords, float roughness) {

	float a = roughness*roughness;
	float a2 = a*a;
	
	float result = (a2 * cos(spherical_coords.x) * sin(spherical_coords.x)) /
					(PI * pow((pow(cos(spherical_coords.x), 2) * (a2 - 1)) + 1, 2));
	return result;
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

vec3[2] ImportanceSampleGGX( vec2 Xi, float Roughness, vec3 N ) {
	float a = Roughness * Roughness;
	a = max(a, 0.001); // WHAT THE F***, How can a^2 appear as a divisor?! -> would cause too high mipmap levels....
	float Phi = 2 * PI * Xi.x;
	float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )) );
	float SinTheta = sqrt( 1 - CosTheta * CosTheta );
	
	vec3 H;
	H.x = SinTheta * cos( Phi );
	H.y = SinTheta * sin( Phi );
	H.z = CosTheta;
	
	vec2 sphericalCoords = cartesianToSpherical(H);
	float pdf = p(vec2(acos(CosTheta), Phi), Roughness);
	
	vec3 UpVector = abs(N.z) < 0.999 ? vec3(0,0,1) : vec3(1,0,0);
	vec3 TangentX = normalize( cross( UpVector, N ) );
	vec3 TangentY = cross( N, TangentX );
	// Tangent to world space
	vec3 result0 = normalize(TangentX * H.x + TangentY * H.y + N * H.z);
	//sphericalCoords = cartesianToSpherical(result0);
	vec3 result1 = vec3(sphericalCoords, pdf);
	
	vec3[2] resultArray;
	resultArray[0] = result0;
	resultArray[1] = result1;
	return resultArray;
}

float chiGGX(float v)
{
    return v > 0 ? 1 : 0;
}

float GGX_PartialGeometryTerm(vec3 v, vec3 n, vec3 h, float alpha)
{
    float VoH2 = clamp(dot(v,h), 0, 1);
    float chi = chiGGX( VoH2 / clamp(dot(v,n), 0, 1) );
    VoH2 = VoH2 * VoH2;
    float tan2 = ( 1 - VoH2 ) / VoH2;
    return (chi * 2) / ( 1 + sqrt( 1 + alpha * alpha * tan2 ) );
}

// This method adjusts the pixel per world area value. This is nessecary because of the non uniform extends of
// a probe, the projected area at the intersection point would be stretched more in a direction in which the pobe
// has a larger extend.
float getAreaPerPixel(int index, vec3 normal) {
	vec3 mini = environmentMapMin[index];
	vec3 maxi = environmentMapMax[index];
	vec3 extents = maxi - mini;
	
	vec3 mainAxis = findMainAxis(normal);
	if(mainAxis.x > 0) {
		return max(extents.y, extents.z) / 256; // TODO: NO HARDCODED RESOLUTION VALUES
	} else if(mainAxis.y > 0) {
		return max(extents.x, extents.z) / 256;
	} else {
		return max(extents.x, extents.y) / 256;
	}
}
vec3[2] boxProjectionAndIntersection(vec3 position_world, vec3 texCoords3d, int probeIndex) {
	vec3 environmentMapMin = environmentMapMin[probeIndex];
	vec3 environmentMapMax = environmentMapMax[probeIndex];
	vec3 posonbox = getIntersectionPoint(position_world, texCoords3d, environmentMapMin, environmentMapMax);
	
	//vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	vec3 environmentMapWorldPosition = environmentMapMin + (environmentMapMax - environmentMapMin)/2.0;
	
	vec3 projectedVector = normalize(posonbox - environmentMapWorldPosition.xyz);
	vec3[2] result;
	result[0] = projectedVector;
	result[1] = posonbox;
	return result;
}
vec3 boxProjection(vec3 position_world, vec3 texCoords3d, int probeIndex) {
	vec3 environmentMapMin = environmentMapMin[probeIndex];
	vec3 environmentMapMax = environmentMapMax[probeIndex];
	vec3 posonbox = getIntersectionPoint(position_world, texCoords3d, environmentMapMin, environmentMapMax);
	
	//vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	vec3 environmentMapWorldPosition = environmentMapMin + (environmentMapMax - environmentMapMin)/2.0;
	
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}
ProbeSample importanceSampleCubeMap(int index, vec3 positionWorld, vec3 normal, vec3 reflected, vec3 v, float roughness, float metallic, vec3 color, int currentMipmapLevel) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 SpecularColor = mix(vec3(0.04,0.04,0.04), color, metallic);
  
  ProbeSample result;
  //result.diffuseColor = textureLod(probes, vec4(boxProjection(positionWorld, reflected, index), index), 0).rgb;
  //return result;

  if(roughness < 0.01) {
  //if(false) {
  	vec3 projectedReflected = reflected;//boxProjection(positionWorld, reflected, index);
    result.specularColor = SpecularColor * textureLod(probes, vec4(projectedReflected, index), 1).rgb;
  	normal = boxProjection(positionWorld, normal, index);
  	result.diffuseColor = diffuseColor * textureLod(probes, vec4(normal, index), MAX_MIPMAPLEVEL).rgb;
  	return result;
  }
  
  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int N = 16;
  vec4 resultDiffuse = vec4(0,0,0,0);
  vec4 resultSpecular = vec4(0,0,0,0);
  float pdfSum = 0;
  float ks = 0;
  float NoV = clamp(dot(n, V), 0.0, 1.0);
  
  for (int k = 0; k < N; k++) {
    vec2 xi = hammersley2d(k, N);
    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, roughness, R);
    vec3 H = importanceSampleResult[0];
    vec2 sphericalCoordsTangentSpace = importanceSampleResult[1].xy;
    //H = hemisphereSample_uniform(xi.x, xi.y, n);
    //vec3[2] projectedVectorAndIntersection = boxProjectionAndIntersection(positionWorld, H, index);
    //float distToIntersection = distance(positionWorld, projectedVectorAndIntersection[1]);
    //H = normalize(projectedVectorAndIntersection[0]);
    
    vec3 L = 2 * dot( V, H ) * H - V;
    
	float NoL = clamp(dot(n, L), 0.0, 1.0);
	float NoH = clamp(dot(n, H), 0.0, 1.0);
	float VoH = clamp(dot(v, H), 0.0, 1.0);
	float alpha = roughness*roughness;
	float alpha2 = alpha * alpha;
	
	if( NoL > 0 )
	{
		vec3 halfVector = normalize(H + v);
		
		float G = GGX_PartialGeometryTerm(v, n, halfVector, alpha) * GGX_PartialGeometryTerm(H, n, halfVector, alpha);
		
		float glossiness = (1-roughness);
		float F0 = 0.02;
		float maxSpecular = mix(0.2, 1.0, metallic);
		F0 = max(F0, (glossiness*maxSpecular));
	    float fresnel = 1; fresnel -= dot(L, H);
		fresnel = pow(fresnel, 5.0);
		float temp = 1.0; temp -= F0;
		fresnel *= temp;
		float F = fresnel + F0;
		
		// Incident light = SampleColor * NoL
		// Microfacet specular = D*G*F / (4*NoL*NoV)
		// pdf = D * NoH / (4 * VoH)
	    //pdfSum += pdf;
	    //float solidAngle = 1/(pdf * N); // contains the solid angle
	    //solidAngle *= roughness;
	    //http://www.eecis.udel.edu/~xyu/publications/glossy_pg08.pdf
	    //float crossSectionArea = (distToIntersection*distToIntersection*solidAngle)/cos(sphericalCoordsTangentSpace.x);
	    //float areaPerPixel = 0.25;
	    //areaPerPixel = getAreaPerPixel(index, normal);
	    //float lod = 0.5*log2((crossSectionArea)/(areaPerPixel));// + roughness * MAX_MIPMAPLEVEL;
	    float lod = roughness * MAX_MIPMAPLEVEL/N;
	    
    	vec4 SampleColor = textureLod(probes, vec4(H, index), lod);
		//SampleColor.rgb = mix(SampleColor.rgb, textureLod(probes, vec4(H, 0), lod).rgb, 1-SampleColor.a);
    	
		vec3 cookTorrance = SampleColor.rgb * clamp((F*G/(4*(NoL*NoV))), 0.0, 1.0);
		ks += fresnel;
		resultSpecular.rgb += clamp(cookTorrance, vec3(0,0,0), vec3(1,1,1));
	}
  }
  
  /*if(pdfSum < 0.9){
  	result.diffuseColor = vec3(1,0,0);
  	return result;
  }*/
  
  resultSpecular = resultSpecular/(N);
  //resultDiffuse = resultDiffuse/(N);
  ks = clamp(ks/N, 0, 1);
  float kd = (1 - ks) * (1 - metallic);
  
  vec3 projectedNormal = boxProjection(positionWorld, normal, index);
  
  const bool MULTIPLE_DIFFUSE_SAMPLES = true;
  const int SAMPLE_COUNT = 16;
  if(MULTIPLE_DIFFUSE_SAMPLES) {
	float lod = MAX_MIPMAPLEVEL;//clamp(currentMipmapLevel-1, 0, MAX_MIPMAPLEVEL);
  	vec3 probeExtents = environmentMapMax[index] - environmentMapMin[index];
  	vec3 probeCenter = environmentMapMin[index] + probeExtents/2;
  	vec3 sampleVector = normal;//reflect(normalize(positionWorld-probeCenter), normal);
  	for (int k = 0; k < SAMPLE_COUNT; k++) {
	    vec2 xi = hammersley2d(k, SAMPLE_COUNT);
	    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, roughness, sampleVector);
	    vec3 H = importanceSampleResult[0];
	    //H = hemisphereSample_uniform(xi.x, xi.y, normal);
		float NoL = clamp(dot(H, normal), 0.0, 1.0);
	    
		resultDiffuse.rgb += NoL * diffuseColor * textureLod(probes, vec4(H, index), lod).rgb * clamp(dot(normal, H), 0, 1);
	  }
	  resultDiffuse.rgb /= SAMPLE_COUNT;
  } else {
  	resultDiffuse.rgb = diffuseColor.rgb * textureLod(probes, vec4(projectedNormal, index), MAX_MIPMAPLEVEL-1).rgb;
  }
  
  result.diffuseColor = resultDiffuse.rgb*kd;
  result.specularColor = resultSpecular.rgb*ks;
  return result;
}

void main()
{
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	vec2 st = vec2(storePos) / vec2(screenWidth, screenHeight);
	
	for(int i = 8; i > 0; i--) {
		float roughness = 0.2 + i * (0.8/8);
		
		vec3 boxHalfExtents = (environmentMapMax[currentProbe] - environmentMapMin[currentProbe])/2;
		vec3 positionWorld = environmentMapMin[currentProbe] + boxHalfExtents;
		vec3 normalWorld = getNormalForTexel(positionWorld, st);
		
		normalWorld = boxProjection(positionWorld, normalWorld, currentProbe);
		
		ProbeSample s = importanceSampleCubeMap(currentProbe, positionWorld, normalWorld, normalWorld, normalWorld, roughness, 1, vec3(1,1,1), i);
		vec4 radianceVisibility = 4*vec4(s.diffuseColor + s.specularColor, 1);
		//radianceVisibility = textureLod(probes, vec4(normalWorld, currentProbe), i);
		
		int index = i;
		if(index == 0) {
			imageStore(out0, storePos, radianceVisibility);
		} else if(index == 1) {
			if(storePos.x > 64 || storePos.y > 64) { return; }
			imageStore(out1, storePos/2, radianceVisibility);
		} else if(index == 2) {
			if(storePos.x > 32 || storePos.y > 32) { return; }
			imageStore(out2, storePos/2/2, radianceVisibility);
		} else if(index == 3) {
			if(storePos.x > 16 || storePos.y > 16) { return; }
			imageStore(out3, storePos/2/2/2, radianceVisibility);
		} else if(index == 4) {
			if(storePos.x > 8 || storePos.y > 8) { return; }
			imageStore(out4, storePos/2/2/2/2, radianceVisibility);
		} else if(index == 5) {
			if(storePos.x > 4 || storePos.y > 4) { return; }
			imageStore(out5, storePos/2/2/2/2/2, radianceVisibility);
		} else if(index == 6) {
			if(storePos.x > 2 || storePos.y > 2) { return; }
			imageStore(out6, storePos/2/2/2/2/2/2, radianceVisibility);
		} else if(index == 7) {
			if(storePos.x > 1 || storePos.y > 1) { return; }
			imageStore(out7, storePos/2/2/2/2/2/2/2, radianceVisibility);
		}
	}
}