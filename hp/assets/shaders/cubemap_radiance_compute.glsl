#define WORK_GROUP_SIZE 32

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding = 0, rgba16f) uniform image2D out0;
layout(binding = 1, rgba16f) uniform image2D out1;
layout(binding = 2, rgba16f) uniform image2D out2;
layout(binding = 3, rgba16f) uniform image2D out3;
layout(binding = 4, rgba16f) uniform image2D out4;
layout(binding = 5, rgba16f) uniform image2D out5;
layout(binding = 6, rgba16f) uniform image2D out6;
layout(binding = 7, rgba16f) uniform image2D out7;

layout(binding = 8) uniform samplerCube currentCubemap;

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
	float visibility;
};

vec3 getNormalForTexel(vec3 positionWorld, vec2 texelPosition, int i) {
	vec3 normal;
	float pot = pow(2, i);
	float inverse = 1/pot;
	
	texelPosition -= inverse + 0.0001; // edge fixup TODO: can't explain yet
	texelPosition *= pot;

	if(currentCubemapSide == 0) { // facing x-dir
		normal = vec3(1, -texelPosition.y, -texelPosition.x);
	} else if(currentCubemapSide == 1) { // facing -x-dir
		normal = vec3(-1, -texelPosition.y, texelPosition.x);
	} else if(currentCubemapSide == 2) { // facing y-dir
		normal = vec3(texelPosition.x, 1, texelPosition.y);
	} else if(currentCubemapSide == 3) { // facing -y-dir
		normal = vec3(texelPosition.x, -1, -texelPosition.y);
	} else if(currentCubemapSide == 4) { // facing z-dir
		normal = vec3(texelPosition.x, -texelPosition.y, 1);
	} else if(currentCubemapSide == 5) { // facing -z-dir
		normal = vec3(-texelPosition.x, -texelPosition.y, -1);
	}
	
	return normalize(normal);
}

vec3 getNormalForTexel(in vec2 txc)
{
  vec3 v;
  switch(currentCubemapSide)
  {
    case 0: v = vec3( 1.0, -txc.x, txc.y); break; // +X
    case 1: v = vec3(-1.0,  txc.x, txc.y); break; // -X
    case 2: v = vec3( txc.x,  1.0, txc.y); break; // +Y
    case 3: v = vec3(-txc.x, -1.0, txc.y); break; // -Y
    case 4: v = vec3(txc.x, -txc.y,  1.0); break; // +Z
    case 5: v = vec3(txc.x,  txc.y, -1.0); break; // -Z
  }
  return normalize(v);
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
  
  ProbeSample result;
  
  {
    result.specularColor = textureLod(currentCubemap, normal, 0).rgb;
  	result.diffuseColor = vec3(0,0,0);
  	result.visibility = 0;
  	//return result;
  }
  
  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int N = 16;
  vec4 resultDiffuse = vec4(0,0,0,0);
  float NoV = clamp(dot(n, V), 0.0, 1.0);
  float totalWeight = 0;
  int counter = 0;
  for (int k = 0; k < N; k++) {
  
    vec2 xi = hammersley2d(k, N);
    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, roughness, R);
    vec3 H = importanceSampleResult[0];
    vec2 sphericalCoordsTangentSpace = importanceSampleResult[1].xy;
    //H = boxProjection(positionWorld, H, currentProbe);
    
    vec3 L = 2 * dot( V, H ) * H - V;

	float NoL = clamp(dot(n, L), 0.0, 1.0);
	float NoH = clamp(dot(n, H), 0.0, 1.0);
	float VoH = clamp(dot(v, H), 0.0, 1.0);
	float alpha = roughness*roughness;
	float alpha2 = alpha * alpha;
	
	if( NoL > 0 )
	{
		vec3 halfVector = normalize(H + v);
		
	    float lod = roughness * MAX_MIPMAPLEVEL/N;
	 
    	vec4 SampleColor = textureLod(currentCubemap, H, lod);
    	
    	result.visibility += SampleColor.a;
       
		result.diffuseColor += SampleColor.rgb * NoL;
		totalWeight += NoL;
		counter ++;
	}
	
  }
  
  result.diffuseColor /= N; // /totalWeight;
  result.visibility /= N;
  return result;
}

void main()
{
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	vec2 st = vec2(storePos) / vec2(screenWidth, screenHeight);
	
	vec4[8] results;
	
	vec3 boxHalfExtents = (environmentMapMax[currentProbe] - environmentMapMin[currentProbe])/2;
	vec3 positionWorld = environmentMapMin[currentProbe] + boxHalfExtents;
	
	for(int i = 0; i < 8; i++) {
	
		if(i == 1 && (storePos.x > 64 || storePos.y > 64)) {
			continue;
		} else if(i == 2 && (storePos.x > 32 || storePos.y > 32)) {
			continue;
		} else if(i == 3 && (storePos.x > 16 || storePos.y > 16)) {
			continue;
		} else if(i == 4 && (storePos.x > 8 || storePos.y > 8)) {
			continue;
		} else if(i == 5 && (storePos.x > 4 || storePos.y > 4)) {
			continue;
		} else if(i == 6 && (storePos.x > 2 || storePos.y > 2)) {
			continue;
		} else if(i == 7 && (storePos.x > 1 || storePos.y > 1)) {
			continue;
		}
	
		float roughness = 0.2 + i * (0.8 / 7);
		vec3 normalWorld = getNormalForTexel(vec3(0,0,0), st, i+1);
		normalWorld = boxProjection(positionWorld, normalWorld, currentProbe);
		ProbeSample s = importanceSampleCubeMap(currentProbe, positionWorld, normalWorld, normalWorld, normalWorld, roughness, 1, vec3(1,1,1), i);
		vec4 radianceVisibility = vec4(s.diffuseColor + s.specularColor, s.visibility);
		results[i] = radianceVisibility;
	}
	
	barrier();

	for(int i = 0; i < 8; i++) {
		int index = i;
		if(index == 0) {
			imageStore(out0, storePos, results[i]);
		} else if(index == 1) {
			if(storePos.x > 64 || storePos.y > 64) { continue; }
			imageStore(out1, storePos, results[i]);
		} else if(index == 2) {
			if(storePos.x > 32 || storePos.y > 32) { continue; }
			imageStore(out2, storePos, results[i]);
		} else if(index == 3) {
			if(storePos.x > 16 || storePos.y > 16) { continue; }
			imageStore(out3, storePos, results[i]);
		} else if(index == 4) {
			if(storePos.x > 8 || storePos.y > 8) { continue; }
			imageStore(out4, storePos, results[i]);
		} else if(index == 5) {
			if(storePos.x > 4 || storePos.y > 4) { continue; }
			imageStore(out5, storePos, results[i]);
		} else if(index == 6) {
			if(storePos.x > 2 || storePos.y > 2) { continue; }
			imageStore(out6, storePos, results[i]);
		} else if(index == 7) {
			if(storePos.x > 1 || storePos.y > 1) { continue; }
			imageStore(out7, storePos, results[i]);
		}
	}
}