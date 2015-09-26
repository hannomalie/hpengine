
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=5) uniform sampler2D lastFrameFinalBuffer;
layout(binding=6) uniform samplerCube globalEnvironmentMap;
layout(binding=7) uniform sampler2D lastFrameReflectionBuffer;
layout(binding=8) uniform samplerCubeArray probes;
layout(binding = 10) uniform samplerCubeArray probePositions;
layout(binding=11) uniform sampler2D ambientLightMap; // diffuse, specular

layout(std430, binding=0) buffer myBlock
{
  float exposure;
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform bool useAmbientOcclusion = true;
uniform bool useSSR = true;
uniform int currentProbe;
uniform bool secondBounce = false;
uniform int N = 12;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];
uniform float environmentMapWeights[100];

in vec2 pass_TextureCoord;

layout(location=0)out vec4 out_environment;
layout(location=1)out vec4 out_refracted;


//include(globals.glsl)

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
struct ProbeSample {
	vec3 diffuseColor;
	vec3 specularColor;
	vec3 refractedColor;
};

bool isInside(vec3 position, vec3 minPosition, vec3 maxPosition) {
	return(all(greaterThanEqual(position, minPosition)) && all(lessThanEqual(position, maxPosition))); 
}

struct Ray {
	vec3 orig, direction;
	float tmin, tmax;
	vec3 invDir;
	int[3] signs;
};

struct Hit {
	bool hit;
	float t0;
	float t1;
};

Hit hits(Ray r, vec3 mini, vec3 maxi) {
	float tmin, tmax, tymin, tymax, tzmin, tzmax;
	Hit result;
	result.hit = false;
	vec3[2] bounds;
	bounds[0] = mini;
	bounds[1] = maxi;
    tmin = (bounds[r.signs[0]].x - r.orig.x) * r.invDir.x;
    tmax = (bounds[1-r.signs[0]].x - r.orig.x) * r.invDir.x;
    tymin = (bounds[r.signs[1]].y - r.orig.y) * r.invDir.y;
    tymax = (bounds[1-r.signs[1]].y - r.orig.y) * r.invDir.y;
    if ((tmin > tymax) || (tymin > tmax))
        return result;
    if (tymin > tmin)
        tmin = tymin;
    if (tymax < tmax)
        tmax = tymax;
    tzmin = (bounds[r.signs[2]].z - r.orig.z) * r.invDir.z;
    tzmax = (bounds[1-r.signs[2]].z - r.orig.z) * r.invDir.z;
    if ((tmin > tzmax) || (tzmin > tmax))
        return result;
    if (tzmin > tmin)
        tmin = tzmin;
    if (tzmax < tmax)
        tmax = tzmax;
    if (tmin > r.tmin) r.tmin = tmin;
    if (tmax < r.tmax) r.tmax = tmax;
    
    result.hit = true;
    result.t0 = tmin;
    result.t1 = tmax;
	return result;
}


struct TraceResult {
	int probeIndexNearest;
	vec3 hitPointNearest;
	vec3 dirToHitPointNearest;
	
	int probeIndexSecondNearest;
	vec3 hitPointSecondNearest;
	vec3 dirToHitPointSecondNearest;
};
TraceResult traceCubes(vec3 positionWorld, vec3 dir, vec3 V, float roughness, float metallic, vec3 color) {
	TraceResult result;
	Ray r;
	r.orig = positionWorld;
	r.direction = dir;
	r.tmin = -5000;
	r.tmax = 5000;
	r.invDir = 1/r.direction;
	r.signs[0] = (r.invDir.x < 0) ? 1 : 0;
    r.signs[1] = (r.invDir.y < 0) ? 1 : 0;
    r.signs[2] = (r.invDir.z < 0) ? 1 : 0;
    
    result.probeIndexNearest = -1;
    result.probeIndexSecondNearest = -1;
    
	float smallestDistOfContainingSample = 99999.0;
	float smallestDistHelperSample = 99999.0;
	
	for(int i = 0; i < activeProbeCount; i++) {
		vec3 mini = environmentMapMin[i];
		vec3 maxi = environmentMapMax[i];
		
		Hit hit = hits(r, mini, maxi);
		if(hit.hit) {
			vec3 boxExtents = (maxi-mini);
			vec3 boxCenter = mini + boxExtents/2;
			vec3 hitPointNearest = r.orig + hit.t0*r.direction;
			vec3 hitPointSecondNearest = r.orig + hit.t1*r.direction;
			vec3 worldDirNearest = normalize(hitPointNearest - boxCenter);
			vec3 worldDirSecondNearest = normalize(hitPointSecondNearest - boxCenter);
			
			const float bias = 0;
			
			// current fragment's world pos is inside volume, only use secondnearest hit, because it is in front of the ray
			// while the nearest would be BEHIND..ray trace is in both directions
			if(isInside(positionWorld,mini,maxi)) {
				if(result.probeIndexNearest != -1) { continue; }
				
				result.probeIndexNearest = i;
				result.hitPointNearest = hitPointSecondNearest;
				result.dirToHitPointNearest = worldDirSecondNearest;
				
			} else if(hit.t1 >= 0) {
				float currentDistHelperSample = distance(positionWorld, hitPointSecondNearest);
				if(currentDistHelperSample < smallestDistHelperSample) {
					smallestDistHelperSample = currentDistHelperSample;
					result.probeIndexSecondNearest = i;
					result.hitPointSecondNearest = hitPointSecondNearest;
					result.dirToHitPointSecondNearest = worldDirSecondNearest;
				}
			}
		}
	}
	return result;
}

// if a direction is very strong, it is taken unless it is the world y axis. Vertical interpolation doesnt work well.
vec3 findMainAxis(vec3 input) {
	if(abs(input.x) > abs(input.z)) {
		return vec3(1,0,0);
	} else {
		return vec3(0,0,1);
	}
	
	// y shouldn't be the main axis ever, I guess
	/*if(abs(input.x) > abs(input.y)) {
		if(abs(input.x) > abs(input.z)) {
			return vec3(1,0,0);
		} else {
			return vec3(0,0,1);
		}
	} else { // y is greater than x
		if(abs(input.y) > abs(input.z)) {
			return vec3(0,1,0);
		} else {
			return vec3(0,0,1);
		}
	}*/
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

//https://seblagarde.wordpress.com/2012/09/29/image-based-lighting-approaches-and-parallax-corrected-cubemap/
vec3 ______boxProjection(vec3 position_world, vec3 texCoords3d, int probeIndex) {
	vec3 environmentMapMin = environmentMapMin[probeIndex];
	vec3 environmentMapMax = environmentMapMax[probeIndex];
	vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	
	vec3 DirectionWS = texCoords3d;
	
	vec3 FirstPlaneIntersect = (environmentMapMax - position_world) / DirectionWS;
	vec3 SecondPlaneIntersect = (environmentMapMin - position_world) / DirectionWS;
	vec3 FurthestPlane = max(FirstPlaneIntersect, SecondPlaneIntersect);
	float Distance = min(min(FurthestPlane.x, FurthestPlane.y), FurthestPlane.z);
	
	vec3 IntersectPositionWS = position_world + DirectionWS * Distance;
	DirectionWS = IntersectPositionWS - environmentMapWorldPosition;
	return DirectionWS;
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
        } else {
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

const float PI = 3.1415926536;
const float MAX_MIPMAPLEVEL = 8;//11; HEMISPHERE is half the cubemap

vec2 _cartesianToSpherical(vec3 cartCoords){
    float outPolar;
    float outElevation;
    
    if (cartCoords.x == 0)
        cartCoords.x = 0.0000001;
        
    float outRadius = 1.0;
    outPolar = atan(cartCoords.z / cartCoords.x);
    
    if (cartCoords.x < 0)
        outPolar += PI;
        
    outElevation = asin(cartCoords.y / outRadius);
    
    return vec2(outPolar, outElevation);
}

vec2 cartesianToSpherical(vec3 cartCoords){
	float a = atan(cartCoords.y/cartCoords.x);
	float b = atan(sqrt(cartCoords.x*cartCoords.x+cartCoords.y*cartCoords.y))/cartCoords.z;
	return vec2(a, b);
}
    
vec3 _sphericalToCartesian(vec2 input){
	vec3 outCart;
    outCart.x = cos(input.x) * sin(input.y);
    outCart.y = sin(input.x) * sin(input.y);
    outCart.z = cos(input.y);
    
    return outCart;
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

// http://holger.dammertz.org/stuff/notes_HammersleyOnHemisphere.html
vec3 hemisphereSample_uniform(float u, float v, vec3 N) {
     float phi = u * 2.0 * PI;
     float cosTheta = 1.0 - v;
     float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
     vec3 result = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);
     
	vec3 UpVector = abs(N.z) < 0.999 ? vec3(0,0,1) : vec3(1,0,0);
	vec3 TangentX = normalize( cross( UpVector, N ) );
	vec3 TangentY = cross( N, TangentX );
	 // Tangent to world space
	 result = TangentX * result.x + TangentY * result.y + N * result.z;
     //mat3 transform = createOrthonormalBasis(N);
	 //result = (transform) * result;
	 
     return result;
}

vec3[2] ImportanceSampleGGX( vec2 Xi, float Roughness, vec3 N ) {
	float a = Roughness * Roughness;
	a = max(a, 0.01); // WHAT THE F***, How can a^2 appear as a divisor?! -> would cause too high mipmap levels....
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
		return min(extents.y, extents.z) / 256; // TODO: NO HARDCODED RESOLUTION VALUES
	} else if(mainAxis.y > 0) {
		return min(extents.x, extents.z) / 256;
	} else {
		return min(extents.x, extents.y) / 256;
	}
}

// https://knarkowicz.wordpress.com/
vec3 EnvDFGPolynomial(vec3 specularColor, float gloss, float ndotv ) {
    float x = gloss;
    float y = ndotv;
 
    float b1 = -0.1688;
    float b2 = 1.895;
    float b3 = 0.9903;
    float b4 = -4.853;
    float b5 = 8.404;
    float b6 = -5.069;
    float bias = clamp(min(b1 * x + b2 * x * x, b3 + b4 * y + b5 * y * y + b6 * y * y * y ), 0.0, 1.0);
 
    float d0 = 0.6045;
    float d1 = 1.699;
    float d2 = -0.5228;
    float d3 = -3.603;
    float d4 = 1.404;
    float d5 = 0.1939;
    float d6 = 2.661;
    float delta = clamp(d0 + d1 * x + d2 * y + d3 * x * x + d4 * x * y + d5 * y * y + d6 * x * x * x, 0.0, 1.0);
    float scale = delta - bias;
 
    bias *= clamp(50.0 * specularColor.y, 0, 1);
    return specularColor * scale + vec3(bias,bias,bias);
}

float dotSaturate(vec3 a, vec3 b) {
	return clamp(dot(a, b),0,1);
}

vec3[2] cubeMapLighting(samplerCube cubemap, vec3 positionWorld, vec3 normalWorld, vec3 reflectedWorld, vec3 v, float roughness, float metallic, vec3 color) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
  float glossiness = pow((1-roughness), 4);// (1-roughness);
  vec3 specularColor = mix(vec3(0.04,0.04,0.04), maxSpecular, glossiness);

  return cookTorranceCubeMap(cubemap,
						v, positionWorld, normalWorld,
						roughness, metallic, diffuseColor, specularColor);
}


ProbeSample importanceSampleProjectedCubeMap(int index, vec3 positionWorld, vec3 normal, vec3 reflected, vec3 v, float roughness, float metallic, vec3 color) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
  float glossiness = pow((1-roughness), 4);// (1-roughness);
  vec3 SpecularColor = mix(vec3(0.04,0.04,0.04), maxSpecular, glossiness);
  
  ProbeSample result;
  //result.diffuseColor = textureLod(probes, vec4(boxProjection(positionWorld, reflected, index), index), 0).rgb;
  //result.specularColor = result.diffuseColor;
  //return result;

  if(roughness < 0.01) {
  	vec3 projectedReflected = boxProjection(positionWorld, reflected, index);
    result.specularColor = SpecularColor * textureLod(probes, vec4(projectedReflected, index), 1).rgb;
  	normal = boxProjection(positionWorld, normal, index);
  	result.diffuseColor = diffuseColor * textureLod(probes, vec4(normal, index), MAX_MIPMAPLEVEL).rgb;
  	
	const bool USE_CONETRACING_FOR_MIRROR = false;
	if (USE_CONETRACING_FOR_MIRROR) {
		TraceResult traceResult = traceCubes(positionWorld, reflected, v, roughness, metallic, color);
		vec4 sampleNearest = textureLod(probes, vec4(traceResult.dirToHitPointNearest, traceResult.probeIndexNearest), 1);
		vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), 1);
		result.specularColor = SpecularColor * mix(sampleNearest, sampleSecondNearest, 1-sampleNearest.a).rgb;
	}
	result.specularColor = textureLod(probes, vec4(projectedReflected, index), 0).rgb;
  	return result;
  }
  
	if(PRECOMPUTED_RADIANCE) {
  		vec3 projectedNormal = boxProjection(positionWorld, normal, index);
  		float NoV = clamp(dot(normal, v), 0.0, 1.0);
    	vec3 R = 2 * dot( v, normal) * normal - v;
  		vec3 projectedReflected = boxProjection(positionWorld, reflected, index);
    	
		TraceResult traceResult;
    	if (USE_CONETRACING_FOR_SPECULAR) {
	    	traceResult = traceCubes(positionWorld, reflected, v, roughness, metallic, color);
	    	projectedReflected = traceResult.dirToHitPointNearest;
			index = traceResult.probeIndexNearest;
	    }
	    
		vec3 mini = environmentMapMin[index];
		vec3 maxi = environmentMapMax[index];
		vec3 extents = maxi -mini;
		vec3 intersection = getIntersectionPoint(positionWorld, normal, mini, maxi);
		float dist = distance(positionWorld, intersection);
		float areaPerPixel = getAreaPerPixel(index, projectedReflected);
		float solidAngle = (1-glossiness);
		float crossSectionArea = (dist*dist*solidAngle) / cos(projectedReflected.x);
		float lod = 0.25 * log2(crossSectionArea/areaPerPixel);
    	
    	vec4 specularSample = textureLod(probes, vec4(projectedReflected, index), (1-glossiness)*MAX_MIPMAPLEVEL);
    	//vec4 specularSample = textureLod(probes, vec4(projectedReflected, index), lod);
    	vec3 biasSample = textureLod(probes, vec4(projectedReflected, index), MAX_MIPMAPLEVEL-2).rgb;
    	vec3 prefilteredColor = specularSample.rgb;
    	//prefilteredColor = textureLod(globalEnvironmentMap, projectedNormal, 0).rgb;
    	vec3 envBRDF = EnvDFGPolynomial(SpecularColor, (glossiness), NoV);
    	
    	result.specularColor = prefilteredColor*envBRDF;
    	// Use unprojected normal for diffuse in precomputed radiance due to poor precision compared to importance sampling method
    	vec3 diffuseSample = textureLod(probes, vec4(normal, index), MAX_MIPMAPLEVEL).rgb;
    	result.diffuseColor = 2*diffuseColor * diffuseSample;
		
    	if (USE_CONETRACING_FOR_DIFFUSE) {
	    	TraceResult traceResult = traceCubes(positionWorld, normal, v, roughness, metallic, color);
	    	vec4 sampleNearest = textureLod(probes, vec4(traceResult.dirToHitPointNearest, traceResult.probeIndexNearest), MAX_MIPMAPLEVEL-1);
	    	vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), MAX_MIPMAPLEVEL-1);
			result.diffuseColor = diffuseColor.rgb * mix(sampleNearest.rgb, sampleSecondNearest.rgb, sampleNearest.a);
	    }
    	
    	if (USE_CONETRACING_FOR_SPECULAR) {
			vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), (1-glossiness)*MAX_MIPMAPLEVEL);
			result.specularColor = mix(result.specularColor, sampleSecondNearest.rgb*envBRDF, 1-specularSample.a).rgb;
    	}
    	
    	return result;
	}
	
  
  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int SAMPLE_COUNT = N;
  vec4 resultDiffuse = vec4(0,0,0,0);
  vec4 resultSpecular = vec4(0,0,0,0);
  float pdfSum = 0;
  float ks = 0;
  float NoV = clamp(dot(n, V), 0.0, 1.0);
  
  for (int k = 0; k < SAMPLE_COUNT; k++) {
    vec2 xi = hammersley2d(k, SAMPLE_COUNT);
    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, roughness, R);
    vec3 H = importanceSampleResult[0];
    vec2 sphericalCoordsTangentSpace = importanceSampleResult[1].xy;
    //H = hemisphereSample_uniform(xi.x, xi.y, n);
    vec3[2] projectedVectorAndIntersection = boxProjectionAndIntersection(positionWorld, H, index);
    float distToIntersection = distance(positionWorld, projectedVectorAndIntersection[1]);
    H = normalize(projectedVectorAndIntersection[0]);
    
    TraceResult traceResult;
    if (USE_CONETRACING_FOR_SPECULAR) {
    	traceResult = traceCubes(positionWorld, importanceSampleResult[0], v, roughness, metallic, color);
    	distToIntersection = distance(positionWorld, traceResult.hitPointNearest);
    	projectedVectorAndIntersection[0] = traceResult.dirToHitPointNearest;
    	projectedVectorAndIntersection[1] = traceResult.hitPointNearest;
		H = normalize(projectedVectorAndIntersection[0]);
    }
    
    vec3 L = 2 * dot( V, H ) * H - V;
    
	float NoL = clamp(dot(n, L), 0.0, 1.0);
	float NoH = clamp(dot(n, H), 0.0, 1.0);
	float VoH = clamp(dot(v, H), 0.0, 1.0);
	float alpha = roughness*roughness;
	float alpha2 = alpha * alpha;
	
	//if( NoL > 0 )
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
	    float pdf = importanceSampleResult[1].z;
    	//float denom = (NoH*NoH*(alpha2-1))+1;
		//float D = alpha2/(3.1416*denom*denom);
	    //pdf = D * NoH / (4 * VoH);
	    //pdf = ((alpha2)/(3.1416*pow(((NoH*NoH*((alpha2)-1))+1), 2))) * NoH / (4 * VoH);
	    pdfSum += pdf;
	    float solidAngle = 1/(pdf * N); // contains the solid angle
	    //solidAngle *= roughness;
	    //http://www.eecis.udel.edu/~xyu/publications/glossy_pg08.pdf
	    float crossSectionArea = (distToIntersection*distToIntersection*solidAngle)/cos(sphericalCoordsTangentSpace.x);
	    float areaPerPixel = 0.25;
	    areaPerPixel = getAreaPerPixel(index, normal);
	    float lod = 0.5*log2((crossSectionArea)/(areaPerPixel));// + roughness * MAX_MIPMAPLEVEL;
	    
    	vec4 SampleColor = textureLod(probes, vec4(H, index), lod);
    	if (USE_CONETRACING_FOR_SPECULAR) {
    		vec4 sampleNearest = textureLod(probes, vec4(H, traceResult.probeIndexNearest), lod);
    		vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), lod);
    		SampleColor = mix(sampleNearest, sampleSecondNearest, 1-sampleNearest.a);
    	}
    	
		vec3 cookTorrance = SpecularColor * SampleColor.rgb * clamp((F*G/(4*(NoL*NoV))), 0.0, 1.0);
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
  float kd = (1 - ks);
  
  vec3 projectedNormal = boxProjection(positionWorld, normal, index);
  
  //const bool MULTIPLE_DIFFUSE_SAMPLES = true;
  if(MULTIPLE_DIFFUSE_SAMPLES) {
	float lod = roughness*MAX_MIPMAPLEVEL;// / SAMPLE_COUNT;
  	vec3 probeExtents = environmentMapMax[index] - environmentMapMin[index];
  	vec3 probeCenter = environmentMapMin[index] + probeExtents/2;
  	vec3 sampleVector = normal;//reflect(normalize(positionWorld-probeCenter), normal);
  	for (int k = 0; k < SAMPLE_COUNT; k++) {
	    vec2 xi = hammersley2d(k, SAMPLE_COUNT);
	    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, 1, sampleVector);
	    vec3 H = importanceSampleResult[0];
	    
  		if(USE_CONETRACING_FOR_DIFFUSE) {
			float lod = 1*MAX_MIPMAPLEVEL;// / SAMPLE_COUNT;
		  	TraceResult traceResult = traceCubes(positionWorld, H, v, roughness, metallic, color);
			vec4 sampleNearest = textureLod(probes, vec4(traceResult.dirToHitPointNearest, traceResult.probeIndexNearest), lod);
			if(traceResult.probeIndexSecondNearest == -1) {
				resultDiffuse.rgb += diffuseColor.rgb * sampleNearest.rgb;
				continue;
			}
			vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), lod);
			
			resultDiffuse.rgb += diffuseColor * mix(sampleNearest, sampleSecondNearest, 1-sampleNearest.a).rgb;
    	} else {
			float lod = MAX_MIPMAPLEVEL;// / SAMPLE_COUNT;
			resultDiffuse.rgb += diffuseColor * textureLod(probes, vec4(H, index), lod).rgb;// * dotSaturate(projectedNormal, H);
    	}
	  }
	  resultDiffuse.rgb /= SAMPLE_COUNT;
  } else {
  	resultDiffuse.rgb = diffuseColor.rgb * textureLod(probes, vec4(projectedNormal, index), MAX_MIPMAPLEVEL).rgb;// * dotSaturate(projectedNormal, vec3(0,1,0));
  }
  
  result.diffuseColor = resultDiffuse.rgb;
  result.specularColor = resultSpecular.rgb;
  return result;
}

float getAmbientOcclusion(vec2 st) {
	
	float ao = 1;
	vec3 ssdo = vec3(0,0,0);
	
	float sum = 0.0;
	float prof = texture(motionMap, st.xy).b; // depth
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
			
			      float prof2g = texture2D(motionMap,st.xy+coords2*rand(st.xy)).b; // depth
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

vec4 getViewDirInTextureSpace(vec3 viewDirection) {
	vec4 projectedCoord = projectionMatrix * vec4(viewDirection, 0);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

#define point2 vec2
#define point3 vec3
 
float distanceSquared(vec2 a, vec2 b) { a -= b; return dot(a, a); }
 
//http://casual-effects.blogspot.de/2014/08/screen-space-ray-tracing.html
vec3 traceScreenSpaceRay1(point3 csOrig, vec3 csDir, mat4x4 proj, vec2 csZBufferSize, float zThickness, 
 						float nearPlaneZ, float stride, float jitter, const float maxSteps, float maxDistance) {
 
    float rayLength = ((csOrig.z + csDir.z * maxDistance) > nearPlaneZ) ? (nearPlaneZ - csOrig.z) / csDir.z : maxDistance;
    point3 csEndPoint = csOrig + csDir * rayLength;
 
    // Project into homogeneous clip space
    //vec4 H0 = proj * vec4(csOrig, 1.0);
    //vec4 H1 = proj * vec4(csEndPoint, 1.0);
    vec4 H0 = getViewPosInTextureSpace(csOrig);
    vec4 H1 = getViewPosInTextureSpace(csEndPoint);
    
    float k0 = 1.0 / H0.w, k1 = 1.0 / H1.w;
 
    // The interpolated homogeneous version of the camera-space points  
    point3 Q0 = csOrig * k0, Q1 = csEndPoint * k1;
 
    // Screen-space endpoints
    point2 P0 = H0.xy * k0;
    point2 P1 = H1.xy * k1;
    
	P0 += 1;
	P0 *= 0.5;
	//return texture(normalMap, (P1), 0).rgb;

    // If the line is degenerate, make it cover at least one pixel
    // to avoid handling zero-pixel extent as a special case later
    P1 += vec2((distanceSquared(P0, P1) < 0.0001) ? 0.01 : 0.0);
    vec2 delta = P1 - P0;
 
    // Permute so that the primary iteration is in x to collapse
    // all quadrant-specific DDA cases later
    bool permute = false;
    if (abs(delta.x) < abs(delta.y)) { 
        // This is a more-vertical line
        permute = true; delta = delta.yx; P0 = P0.yx; P1 = P1.yx; 
    }
 
    float stepDir = sign(delta.x);
    float invdx = stepDir / delta.x;
 
    // Track the derivatives of Q and k
    vec3  dQ = (Q1 - Q0) * invdx;
    float dk = (k1 - k0) * invdx;
    vec2  dP = vec2(stepDir, delta.y * invdx);
 
    // Scale derivatives by the desired pixel stride and then
    // offset the starting values by the jitter fraction
    dP *= stride; dQ *= stride; dk *= stride;
    P0 += dP * jitter; Q0 += dQ * jitter; k0 += dk * jitter;
 
    // Slide P from P0 to P1, (now-homogeneous) Q from Q0 to Q1, k from k0 to k1
    point3 Q = Q0; 
 
    // Adjust end condition for iteration direction
    float  end = P1.x * stepDir;
 
    float k = k0, stepCount = 0.0, prevZMaxEstimate = csOrig.z;
    float rayZMin = prevZMaxEstimate, rayZMax = prevZMaxEstimate;
    float sceneZMax = rayZMax + 100;
    vec2 hitPixel;
    for (point2 P = P0; 
         ((P.x * stepDir) <= end) && (stepCount < maxSteps) &&
         ((rayZMax < sceneZMax - zThickness) || (rayZMin > sceneZMax)) &&
          (sceneZMax != 0); 
         P += dP, Q.z += dQ.z, k += dk, ++stepCount) {
         
        rayZMin = prevZMaxEstimate;
        rayZMax = (dQ.z * 0.5 + Q.z) / (dk * 0.5 + k);
        prevZMaxEstimate = rayZMax;
        if (rayZMin > rayZMax) { 
           float t = rayZMin; rayZMin = rayZMax; rayZMax = t;
        }

        hitPixel = permute ? P.yx : P;
        
        // You may need hitPixel.y = csZBufferSize.y - hitPixel.y; here if your vertical axis
        // is different than ours in screen space
        
		//P0 += 1;
		//P0 *= 0.5;
        sceneZMax = texelFetch(motionMap, ivec2(hitPixel), 0).b;
    }
     
    // Advance Q based on the number of steps
    Q.xy += dQ.xy * stepCount;
    vec3 hitPoint = Q * (1.0 / k);
    bool hit = (rayZMax >= sceneZMax - zThickness) && (rayZMin < sceneZMax);

    return texelFetch(lightAccumulationMap, ivec2(hitPixel), 0).rgb;
}

vec3 __rayCast(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness, float metallic) {
	return traceScreenSpaceRay1(targetPosView, normalize(targetNormalView), projectionMatrix, vec2(1280, 720), 1000, 0.1, 1, 0, 25, 500);
	//vec3 traceScreenSpaceRay1(point3 csOrig, vec3 csDir, mat4x4 proj, vec2 csZBufferSize, float zThickness, 
 	//					float nearPlaneZ, float stride, float jitter, const float maxSteps, float maxDistance) {
}

// ray in screen space....
vec3 _rayCast(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness, float metallic) {

	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = 10*normalize(reflect(eyeToSurfaceView, targetNormalView));
	
	vec4 P0 = getViewPosInTextureSpace(targetPosView);
	vec4 P1 = getViewPosInTextureSpace(targetPosView+reflectionVecView);
	
	vec2 viewRay = normalize(P1.xy - P0.xy);
	viewRay *= 0.1;
	
	vec2 currentScreenPos = screenPos;
	vec3 currentViewPos = targetPosView;
	
	const int MAX_STEPS = 100;
	int steps = 0;
		  	
	while(currentScreenPos.x < 1 && currentScreenPos.x > 0 &&
		  currentScreenPos.y < 1 && currentScreenPos.y > 0 && steps < MAX_STEPS) {
	
		  steps++;
		  currentScreenPos.xy += viewRay.xy;
		  
		  vec3 currentPosSample = textureLod(positionMap, currentScreenPos.xy, 0).xyz;
		  
		  float difference = currentViewPos.z - currentPosSample.z;
		  if (difference > 0) {
		  	
		  	currentScreenPos -= viewRay;
		    currentViewPos = texture2D(positionMap, currentScreenPos.xy).xyz;
		  	
		  	while(currentScreenPos.x < 1 && currentScreenPos.x > 0 &&
		  		  currentScreenPos.y < 1 && currentScreenPos.y > 0 && steps < MAX_STEPS) {
		  
		  		  currentPosSample = texture2D(positionMap, currentScreenPos.xy).xyz;
		  
				  float absDifference = distance(currentViewPos.z, currentPosSample.z);
				  if (absDifference > 0.01) {
  		  		  	break;
				  }
		  		currentScreenPos += viewRay;
		  		currentViewPos = currentPosSample;
		 		steps++;
		  	}
		  	
		  	vec4 resultCoords = getViewPosInTextureSpace(currentPosSample);
		  	vec4 lightDiffuseSpecular = 0.25*blur(lastFrameFinalBuffer, resultCoords.xy, roughness/10, 0); // compensation for *4 intensity
		  	return lightDiffuseSpecular.rgb;
		  }
		  
		  currentViewPos = currentPosSample;
	}
	return probeColor;
}

vec3 rayCast(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness, float metallic) {

//return color;
//return probeColor;

	vec3 eyeToSurfaceView = targetPosView;
	vec3 reflectionVecView = normalize(reflect(eyeToSurfaceView, targetNormalView));
	
	int STEPRAYLENGTH = 8;
	vec3 viewRay = STEPRAYLENGTH*normalize(reflectionVecView);
	
	vec3 currentViewPos = targetPosView;
	
	float fadeToViewer = dot(reflectionVecView, vec3(0,0,-1))+1;
	fadeToViewer /= 2;
	if(fadeToViewer == 0) { return probeColor; }
	
	const int STEPS_1 = 60;
	const int STEPS_2 = 30;
	for (int i = 0; i < STEPS_1; i++) {
	
		  currentViewPos += viewRay;
		  
		  vec3 currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
		  float difference = currentViewPos.z - currentPosSample.z;
		  const float THICKNESS_THRESHOLD = 70.0f;
		  if (difference < 0) {
		  	const bool objectInFrontOfStartPoint = currentViewPos.z > targetPosView.z;
		  	if(objectInFrontOfStartPoint) { continue;}
		  
		  	
		  	currentViewPos -= viewRay;
		  	
		  	for(int x = 0; x < STEPS_2; x++) {
		 		currentViewPos += viewRay/STEPS_2;
		  		currentPosSample = texture2D(positionMap, getViewPosInTextureSpace(currentViewPos).xy).xyz;
		  
				  float absDifference = distance(currentViewPos.z, currentPosSample.z);
				  if (absDifference < 0.01) {
  		  		  	break;
				  }
		  	}
		  	
  		  	vec4 resultCoords = getViewPosInTextureSpace(currentViewPos);
  			if (resultCoords.x > 0 && resultCoords.x < 1 && resultCoords.y > 0 && resultCoords.y < 1)
			{
				vec3 targetPositionWorld = (inverse(viewMatrix) * vec4(targetPosView,1)).xyz;
				vec3 targetNormalWorld = (inverse(viewMatrix) * vec4(targetNormalView,0)).xyz;
				
				float distanceInWorld = distance(currentPosSample, targetPositionWorld);
				float distanceInWorldPercent = distanceInWorld / 25;
    			float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))*2), 0, 1);
    			//float screenEdgefactor = clamp((distance(resultCoords.xy, vec2(0.5,0.5))-0.5)*2, 0, 1);
    			float mipMapChoser = roughness * 11;
    			mipMapChoser *= distanceInWorldPercent;
    			mipMapChoser = max(mipMapChoser, screenEdgefactor * 3);
    			mipMapChoser = min(mipMapChoser, 2);
    			
    			float threshold = 0.65;
				float maxDist = distance(1.0, 0.5);
				float dist = distance(screenPos.xy, vec2(0.5,0.5));
				float percent = (dist/maxDist);
				float screenEdgeFactor = smoothstep(1, 0, percent-threshold);
    			
    			vec4 diffuseColorMetallic = textureLod(diffuseMap, screenPos.xy, mipMapChoser);
			  	vec3 diffuseColor = mix(diffuseColorMetallic.rgb, vec3(0.0,0.0,0.0), diffuseColorMetallic.a);
				vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
				float glossiness = pow((1-roughness), 4);
				vec3 specularColor = mix(vec3(0.04,0.04,0.04), maxSpecular, glossiness);
    			specularColor = EnvDFGPolynomial(specularColor, (glossiness), 0);
    			
    			vec4 motionVecProbeIndices = texture2D(motionMap, resultCoords.xy);
  				vec2 motion = 0.25*motionVecProbeIndices.xy;

    			vec3 ambientSample = ambientColor*blur(ambientLightMap, resultCoords.xy, roughness*0.05f, mipMapChoser).rgb;
    			vec3 lightSample = blur(lightAccumulationMap, resultCoords.xy, roughness*0.1f, mipMapChoser).rgb;
    			vec3 thisFrameLighting = ambientSample+lightSample;
					//thisFrameLighting.rgb = Uncharted2Tonemap(exposure*thisFrameLighting.rgb);
					//vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(11.2,11.2,11.2));
					//thisFrameLighting.rgb = thisFrameLighting.rgb * whiteScale;
	    			
    			vec3 reflectedColor;
    			if(SSR_TEMPORAL_FILTERING) {
	    			vec4 lightDiffuseSpecular = 0.5*blur(lastFrameFinalBuffer, resultCoords.xy-motion, roughness*0.05f, mipMapChoser);
    				reflectedColor = (lightDiffuseSpecular.rgb + thisFrameLighting)/2.0f;
    			} else {
    				reflectedColor = ambientSample;//thisFrameLighting;
    			}
    			//reflectedColor = 0.5*blur(ambientLightMap, resultCoords.xy, roughness*0.1f, mipMapChoser).rgb + 0.5*blur(lightAccumulationMap, resultCoords.xy, roughness*0.1f, mipMapChoser).rgb;
    			//return specularColor*reflectedColor;
    			
    			vec3 lightDirection = currentPosSample - targetPositionWorld;
    			
    			float mixer;
    			if(SSR_FADE_TO_SCREEN_BORDERS) {
    				mixer = 1-pow(screenEdgefactor,1);
    			} else {
    				mixer = 1;
    			}
    			
    			//mixer = clamp(mixer, 0, 1);
    			//mixer *= fadeToViewer;
			//return vec3(mixer,mixer,mixer);
    			vec3 result = mix(probeColor, reflectedColor, mixer);
				return specularColor*result;
		  	}
		  	//return vec3(1,0,0);
		  	//color = texture(environmentMap, normalize(normalize((inverse(viewMatrix) * vec4(reflectionVecView,0)).xyz))).rgb;
			return probeColor;
		  }
	}
	
	return probeColor;
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

const bool NO_INTERPOLATION_IF_ONE_PROBE_CACHED = true;
const bool USE_CACHED_RPROBES = false;

struct BoxIntersectionResult {
	int indexNearest;
	int indexSecondNearest;
	
	vec3 intersectionNormalNearest;
	vec3 intersectionReflectedNearest;
	
	vec3 intersectionNormalSecondNearest;
	vec3 intersectionReflectedSecondNearest;
};

BoxIntersectionResult getTwoNearestProbeIndicesAndIntersectionsForPosition(vec3 position, vec3 V, vec3 normal, vec2 uv) {
	BoxIntersectionResult result;
	result.indexNearest = -1;
	result.indexSecondNearest = -1;
	
	vec3 reflectionVector = normalize(reflect(V, normal));
	
	vec3 currentEnvironmentMapMin1 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax1 = environmentMapMax[0];
	vec3 currentEnvironmentMapMin2 = environmentMapMin[0];
	vec3 currentEnvironmentMapMax2 = environmentMapMax[0];
	vec3 currentCenter1 = currentEnvironmentMapMin1 + vec3(distance(currentEnvironmentMapMin1.x, currentEnvironmentMapMax1.x), distance(currentEnvironmentMapMin1.y, currentEnvironmentMapMax1.y), distance(currentEnvironmentMapMin1.z, currentEnvironmentMapMax1.z))/2.0;
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
	
	currentEnvironmentMapMin1 = environmentMapMin[iForNearest1];
	currentEnvironmentMapMax1 = environmentMapMax[iForNearest1];
	currentEnvironmentMapMin2 = environmentMapMin[iForNearest2];
	currentEnvironmentMapMax2 = environmentMapMax[iForNearest2];
	
	result.indexNearest = iForNearest1;
	result.intersectionNormalNearest = getIntersectionPoint(position, normal, currentEnvironmentMapMin1, currentEnvironmentMapMax1);
	result.intersectionReflectedNearest = getIntersectionPoint(position, reflectionVector, currentEnvironmentMapMin1, currentEnvironmentMapMax1);
	
	result.indexSecondNearest = iForNearest2;
	result.intersectionNormalSecondNearest = getIntersectionPoint(position, normal, currentEnvironmentMapMin2, currentEnvironmentMapMax2);
	result.intersectionReflectedSecondNearest = getIntersectionPoint(position, reflectionVector, currentEnvironmentMapMin2, currentEnvironmentMapMax2);
	
	return result;
}

float calculateWeight(vec3 positionWorld, vec3 minimum, vec3 maximum, vec3 minimum2, vec3 maximum2) {
	vec3 centerNearest = minimum + (maximum - minimum)/2.0;
	vec3 centerSecondNearest = minimum2 + (maximum2 - minimum2)/2.0;
	
	vec3 intersectionAreaMinimum = vec3(max(minimum2.x, minimum.x), max(minimum2.y, minimum.y), max(minimum2.z, minimum.z));
	vec3 intersectionAreaMaximum = vec3(min(maximum2.x, maximum.x), min(maximum2.y, maximum.y), min(maximum2.z, maximum.z));
	vec3 intersectionAreaExtends = (intersectionAreaMaximum - intersectionAreaMinimum);
	vec3 intersectionAreaHalfExtends = intersectionAreaExtends/2.0;
	vec3 interpolationAreaCenter = intersectionAreaMinimum + intersectionAreaHalfExtends;
	
	vec3 interpolationMainAxis = centerSecondNearest - interpolationAreaCenter;
	vec3 axisFlip = sign(interpolationMainAxis); // contains information when to flip the interpolation
	interpolationMainAxis = findMainAxis(interpolationMainAxis);
	
	vec3 interpolationOffsetHelper = vec3(2,2,2) - interpolationMainAxis; // containts 2 for axis that should be adjusted, 1 for main axis
	vec3 interpolationOffsetHelper2 = vec3(1,1,1) - interpolationMainAxis; // containts 1 for axis that should be adjusted, 0 for main axis
	
	vec3 translationAmount = intersectionAreaMinimum;
	
	intersectionAreaMaximum -= translationAmount;
	intersectionAreaMinimum -= translationAmount; // -> Makes it zero, hopefully :D
	vec3 positionInIntersectionAreaSpace = positionWorld - translationAmount;
	
	vec3 percent = positionInIntersectionAreaSpace / intersectionAreaExtends; // contains percent of which the position is between min and max of interpolation area
	
	percent *= interpolationOffsetHelper; // now contains the doubled percents (from 0 to 2) for all non-main interpolation axes, 0 to 1 for main axis
	percent -= interpolationOffsetHelper2; // now contains values from -1 to 1 for all non-main interpolation axes, 0 to 1 for main axis
	percent.x = abs(percent.x);
	percent.y = abs(percent.y);
	percent.z = abs(percent.z); // now contains values between 0 and 1 for each axis
	
	float result;
	if(interpolationMainAxis.x > 0) {
		result = min(percent.x, min(1-percent.y, 1-percent.z));
		result = percent.x;
		if(axisFlip.x < 0) { result = 1 - result; }
	} else {
		result = min(percent.z, min(1-percent.x, 1-percent.y));
		result = percent.z;
		if(axisFlip.z < 0) { result = 1 - result; }
	}
	return result;
}

ProbeSample getProbeColors(vec3 positionWorld, vec3 V, vec3 normalWorld, float roughness, float metallic, vec2 uv, vec3 color) {
	ProbeSample result;
	
	float mipMapLevel = roughness * MAX_MIPMAPLEVEL;
	float mipMapLevelSecond = mipMapLevel;
	
	BoxIntersectionResult twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForPosition(positionWorld, V, normalWorld, uv);
	int probeIndexNearest = twoIntersectionsAndIndices.indexNearest;
	int probeIndexSecondNearest = twoIntersectionsAndIndices.indexSecondNearest;
	vec3 intersectionNearest = twoIntersectionsAndIndices.intersectionNormalNearest;
	vec3 intersectionSecondNearest = twoIntersectionsAndIndices.intersectionNormalSecondNearest;
	
	const bool useLagardeMethod = true;
	if(useLagardeMethod) {
		int overlappingVolumesCount = 0;
		
		const int k = 4;
		int[k] indices;
		float[k] distances;
		float[k] weights;
		float[k] customWeights;
		float[k] blendFactors;
		for(int i = 0; i < k; i++) {
			indices[i] = -1;
			distances[i] = 100000f;
			weights[i] = 0.0f;
			blendFactors[i] = 0.0f;
			customWeights[i] = 1.0;
		}
		
		for(int i = 0; i < activeProbeCount && overlappingVolumesCount < k; i++) {
			vec3 currentEnvironmentMapMin = environmentMapMin[i];
			vec3 currentEnvironmentMapMax = environmentMapMax[i];
			vec3 currentEnvironmentMapExtents = currentEnvironmentMapMax - currentEnvironmentMapMin;
			vec3 currentEnvironmentMapHalfExtents = currentEnvironmentMapExtents/2.0f;
			vec3 currentEnvironmentMapCenter = currentEnvironmentMapMin + currentEnvironmentMapHalfExtents;
		if(!isInside(positionWorld, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
			
			vec3 positionInBoxSpace = positionWorld - currentEnvironmentMapCenter;
			vec3 positionInPositiveBoxSpace = vec3(abs(positionInBoxSpace.x),abs(positionInBoxSpace.y),abs(positionInBoxSpace.z));
			vec3 percentVec = positionInPositiveBoxSpace / (currentEnvironmentMapExtents/2.0f);
			float percent = max(percentVec.x, max(percentVec.y, percentVec.z));
			const float interpolationThresholdPercent = 0.5f; // means interpolation starts after position is within the last 20 percent to border
			percent -= interpolationThresholdPercent;
			float minimumPercent = (percent/(1.0f-interpolationThresholdPercent));
			
			vec3 BoxInnerRange = (interpolationThresholdPercent * currentEnvironmentMapHalfExtents);
			BoxInnerRange = interpolationThresholdPercent * currentEnvironmentMapHalfExtents;
			vec3 BoxOuterRange = (currentEnvironmentMapHalfExtents);
			vec3 numerator = (positionInPositiveBoxSpace - BoxInnerRange);
			numerator = clamp(numerator, vec3(0.0f,0.0f,0.0f), numerator);
			vec3 denominator = (BoxOuterRange - BoxInnerRange);
			positionInPositiveBoxSpace = numerator / denominator;
		 	//positionInPositiveBoxSpace = (positionInPositiveBoxSpace) / (BoxOuterRange);
			minimumPercent = max(positionInPositiveBoxSpace.x, max(positionInPositiveBoxSpace.y, positionInPositiveBoxSpace.z));
			
	//result.diffuseColor = result.diffuseColor = vec3(minimumPercent,minimumPercent,minimumPercent); return result;
	
			float dist = distance(positionWorld, currentEnvironmentMapCenter);
			
			indices[overlappingVolumesCount] = i;
			distances[overlappingVolumesCount] = dist;
			weights[overlappingVolumesCount] = minimumPercent * (environmentMapWeights[i]);
			customWeights[overlappingVolumesCount] = environmentMapWeights[i];
			overlappingVolumesCount++;
		}
		
		float weightSum = 0.0f;
		float invWeightSum = 0.0f;
		for(int i = 0; i < overlappingVolumesCount; i++) {
			weightSum = weightSum + weights[i];
			invWeightSum = invWeightSum + clamp(customWeights[i] - weights[i], 0.0f, 1.0f);
		}
		
		float SumBlendFactor = 0.0f;
		for(int i = 0; i < overlappingVolumesCount; i++) {
			//if(weightSum == 0.0f || invWeightSum == 0.0f) { blendFactors[i] = 1.0f; continue; }
			//blendFactors[i] = (1.0f - (weights[i] / weightSum)) / (overlappingVolumesCount - 1.0f);
	        //blendFactors[i] = blendFactors[i] * ((1.0f - weights[i]) / invWeightSum);
	        blendFactors[i] = clamp(customWeights[i] - weights[i], 0.0f, 1.0f) / invWeightSum;
	        blendFactors[i] /= overlappingVolumesCount;
	        SumBlendFactor += blendFactors[i];
		}
		if (SumBlendFactor == 0.0f) {
	        SumBlendFactor = 1.0f;
	    }
	    float ConstVal = 1.0f / SumBlendFactor;
		for(int i = 0; i < overlappingVolumesCount; i++) {
			blendFactors[i] = blendFactors[i] * ConstVal;
		}
		
		if(overlappingVolumesCount == 1) { blendFactors[0] = 1.0f; SumBlendFactor = 1.0f;}

		const bool USE_GLOBAL_ENVIRONMENT_MAP = true;
		if(USE_GLOBAL_ENVIRONMENT_MAP && overlappingVolumesCount == 0) {
			vec3[2] diffuseSpecular = cubeMapLighting(globalEnvironmentMap, positionWorld,
									normalWorld, reflect(V, normalWorld), V,
									roughness, metallic, color);
			result.diffuseColor = diffuseSpecular[0];
			result.specularColor = diffuseSpecular[1];
			return result;
		}

		for(int i = 0; i < overlappingVolumesCount; i++) {
			ProbeSample s = importanceSampleProjectedCubeMap(indices[i], positionWorld, normalWorld, reflect(V, normalWorld), V, roughness, metallic, color);
			float blendFactor = blendFactors[i];//clamp(blendFactors[i], 0.0f, 1.0f);
			result.diffuseColor += s.diffuseColor * blendFactor;
			result.specularColor += s.specularColor * blendFactor;
			result.refractedColor += textureLod(probes, vec4(boxProjection(positionWorld, refract(V, normalWorld, 1-roughness), indices[i]), indices[i]), roughness*MAX_MIPMAPLEVEL-4).rgb * blendFactor;
						
			/*if(overlappingVolumesCount == 2)
			{
				result.diffuseColor = vec3(0,1,0);
				result.diffuseColor = vec3(0,blendFactors[0],0);
				result.specularColor = result.diffuseColor;
				return result;
			}
			if(blendFactors[0] == 0.0f)
			{
				result.diffuseColor = vec3(1,0,0);
				result.specularColor = result.diffuseColor;
				return result;
			}
			*/
		}
		
		return result;
	}
	
	vec3 normal = normalize(normalWorld);
	vec3 reflected = normalize(reflect(V, normalWorld));

	float mixer = calculateWeight(positionWorld, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest],
									 environmentMapMin[probeIndexSecondNearest], environmentMapMax[probeIndexSecondNearest]);
	
	bool onlyFirstProbeFound = (probeIndexNearest != -1 && probeIndexSecondNearest == -1) || (probeIndexNearest != -1 && probeIndexNearest == probeIndexSecondNearest);
	bool noProbeFound = probeIndexNearest == -1 && probeIndexSecondNearest == -1;
	
	vec3 boxProjectedRefractedNearest = boxProjection(positionWorld, refract(V, normalWorld, 1-roughness), probeIndexNearest);
	// early out
	if(onlyFirstProbeFound) {
		result = importanceSampleProjectedCubeMap(probeIndexNearest, positionWorld, normal, reflected, V, roughness, metallic, color);
		result.refractedColor = textureLod(probes, vec4(boxProjectedRefractedNearest, probeIndexNearest), roughness*MAX_MIPMAPLEVEL-4).rgb;
		return result;
	} else if(noProbeFound) {
		//vec4 tempDiffuse = textureLod(globalEnvironmentMap, texCoords3d, mipMapLevel);
		//vec4 tempSpecular = textureLod(globalEnvironmentMap, texCoords3dSpecular, mipMapLevel);
		result.diffuseColor = vec3(0,0,0);
		result.specularColor = vec3(0,0,0);
		return result;
	}
	
	mipMapLevel *= clamp(distance(positionWorld, intersectionNearest)/10, 0, 1);
	mipMapLevelSecond *= clamp(distance(positionWorld, intersectionSecondNearest)/10, 0, 1);
	vec3 diffuseNearest;// = textureLod(probes, vec4(boxProjectedNearest, probeIndexNearest), mipMapLevel).rgb;
	vec3 specularNearest;// = textureLod(probes, vec4(boxProjectedNearestSpecular, probeIndexNearest), mipMapLevel).rgb;
	vec3 diffuseSecondNearest;// = textureLod(probes, vec4(boxProjectedSecondNearest, probeIndexSecondNearest), mipMapLevelSecond).rgb;
	vec3 specularSecondNearest;// = textureLod(probes, vec4(boxProjectedSecondNearest, probeIndexSecondNearest), mipMapLevelSecond).rgb;
	
	ProbeSample s = importanceSampleProjectedCubeMap(probeIndexNearest, positionWorld, normal, reflected, V, roughness, metallic, color);
	diffuseNearest = s.diffuseColor;
	specularNearest = s.specularColor;

	s = importanceSampleProjectedCubeMap(probeIndexSecondNearest, positionWorld, normal, reflected, V, roughness, metallic, color);
	diffuseSecondNearest = s.diffuseColor;
	specularSecondNearest = s.specularColor;
	
	result.diffuseColor = mix(diffuseNearest, diffuseSecondNearest, mixer);
	result.specularColor = mix(specularNearest, specularSecondNearest, mixer);
	//result[0] = vec3(mixer, 0, 0);
	//result[1] = vec3(mixer, 0, 0);
	
	result.refractedColor = textureLod(probes, vec4(boxProjectedRefractedNearest, probeIndexNearest), roughness*MAX_MIPMAPLEVEL-4).rgb;
	return result;
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
	V = normalize(inverse(viewMatrix) * vec4(positionView,0)).xyz;
	vec4 colorMetallic = textureLod(diffuseMap, st, 0);
	vec3 color = colorMetallic.rgb;
	float roughness = positionViewRoughness.a;
	float metallic = colorMetallic.a;
	
	ProbeSample probeColorsDiffuseSpecular = getProbeColors(positionWorld, V, normalWorld, roughness, metallic, st, color);
	
	
	if(useSSR && roughness < 0.2)
	{
		vec3 tempSSLR = rayCast(color, probeColorsDiffuseSpecular.specularColor.rgb, st, positionView, normalView.rgb, roughness, metallic);
		probeColorsDiffuseSpecular.specularColor = tempSSLR;
	}
	
	vec3 result = probeColorsDiffuseSpecular.diffuseColor + probeColorsDiffuseSpecular.specularColor;
	
	out_environment.rgb = result;
	//out_environment.a = getAmbientOcclusion(st);
	out_refracted.rgb = probeColorsDiffuseSpecular.refractedColor;
}