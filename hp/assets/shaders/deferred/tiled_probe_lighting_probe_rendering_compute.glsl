
#define WORK_GROUP_SIZE 16

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding = 0) uniform sampler2D positionMap;
layout(binding = 1) uniform sampler2D normalMap;
layout(binding = 2) uniform sampler2D diffuseMap;
layout(binding = 6, rgba16f) uniform image2D out_environment;

layout(binding = 8) uniform samplerCubeArray probes;
layout(binding = 9) uniform samplerCube environmentProbe;
layout(binding = 10) uniform samplerCubeArray probePositions;

uniform float screenWidth;
uniform float screenHeight; 
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];
uniform int activeProbeCount;
uniform int currentProbe;
uniform bool secondBounce = false;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

const float PI = 3.1415926536;
const float MAX_MIPMAPLEVEL = 8; // HEMISPHERE is half the cubemap

struct ProbeSample {
	vec3 diffuseColor;
	vec3 specularColor;
	vec3 refractedColor;
};

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

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

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
				//vec4 ownBoxSamplePosition = textureLod(probePositions, vec4(worldDirSecondNearest, i), 0);
				// the traced sample is inside the box, no alpha blending required because we didn't shoot in a hole
				//if(length(ownBoxSamplePosition.xyz) < length(hitPointSecondNearest-boxCenter) + bias)
				{
					result.probeIndexNearest = i;
					result.hitPointNearest = hitPointSecondNearest;
					result.dirToHitPointNearest = worldDirSecondNearest;
					//return result;
					continue;
				}
			} else {
				float currentDistHelperSample = distance(positionWorld, hitPointSecondNearest);
				if(currentDistHelperSample < smallestDistHelperSample) {
					//vec4 positionHelperSample = textureLod(probePositions, vec4(worldDirSecondNearest, i), 0);
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

ProbeSample importanceSampleProjectedCubeMap(int index, vec3 positionWorld, vec3 normal, vec3 reflected, vec3 v, float roughness, float metallic, vec3 color) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
  float glossiness = pow((1-roughness), 4);// (1-roughness);
  vec3 SpecularColor = mix(vec3(0.04,0.04,0.04), maxSpecular, glossiness);
  
  ProbeSample result;
  //result.diffuseColor = textureLod(probes, vec4(boxProjection(positionWorld, reflected, index), index), 0).rgb;
  //return result;
  
	if(PRECOMPUTED_RADIANCE) {
  		vec3 projectedNormal = boxProjection(positionWorld, normal, index);
  		vec3 projectedReflected = boxProjection(positionWorld, reflect(v, normal), index);
  		float NoV = clamp(dot(normal, v), 0.0, 1.0);
    	vec3 R = 2 * dot( v, normal) * normal - v;
    	
    	
		vec3 mini = environmentMapMin[index];
		vec3 maxi = environmentMapMax[index];
		vec3 extents = maxi -mini;
		vec3 intersection = getIntersectionPoint(positionWorld, normal, mini, maxi);
		float dist = distance(positionWorld, intersection);
		float distanceBias = (roughness) / (dist*dist);
    	
    	vec3 prefilteredColor = textureLod(probes, vec4(projectedReflected, index), (1-glossiness)*MAX_MIPMAPLEVEL).rgb;
    	//prefilteredColor = textureLod(globalEnvironmentMap, projectedNormal, 0).rgb;
    	vec3 envBRDF = EnvDFGPolynomial(SpecularColor, (glossiness), NoV);
    	
    	result.specularColor = prefilteredColor * envBRDF;
    	// Use unprojected normal for diffuse in precomputed radiance due to poor precision compared to importance sampling method
    	vec3 diffuseSample = textureLod(probes, vec4(projectedNormal, index), MAX_MIPMAPLEVEL).rgb;
    	result.diffuseColor = diffuseColor * diffuseSample;
    	return result;
	}
	
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
  	return result;
  }
  
  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int N = 12;
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
    vec3[2] projectedVectorAndIntersection = boxProjectionAndIntersection(positionWorld, H, index);
    float distToIntersection = distance(positionWorld, projectedVectorAndIntersection[1]);
    H = normalize(projectedVectorAndIntersection[0]);
    
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
	    //http://www.eecis.udel.edu/~xyu/publications/glossy_pg08.pdf
	    float crossSectionArea = (distToIntersection*distToIntersection*solidAngle)/cos(sphericalCoordsTangentSpace.x);
	    float areaPerPixel = 0.25;
	    areaPerPixel = getAreaPerPixel(index, normal);
	    float lod = 0.5*log2((crossSectionArea)/(areaPerPixel));// + roughness * MAX_MIPMAPLEVEL;
	    
    	vec4 SampleColor = textureLod(probes, vec4(H, index), lod);
		//SampleColor.rgb = mix(SampleColor.rgb, textureLod(probes, vec4(H, 0), lod).rgb, 1-SampleColor.a);
    	
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
  float kd = (1 - ks) * (1 - metallic);
  
  vec3 projectedNormal = boxProjection(positionWorld, normal, index);
  
  const bool MULTIPLE_DIFFUSE_SAMPLES = true;
  const int SAMPLE_COUNT = 8;
  if(MULTIPLE_DIFFUSE_SAMPLES) {
	float lod = roughness*MAX_MIPMAPLEVEL;// / SAMPLE_COUNT;
  	vec3 probeExtents = environmentMapMax[index] - environmentMapMin[index];
  	vec3 probeCenter = environmentMapMin[index] + probeExtents/2;
  	vec3 sampleVector = normal;//reflect(normalize(positionWorld-probeCenter), normal);
  	for (int k = 0; k < SAMPLE_COUNT; k++) {
	    vec2 xi = hammersley2d(k, SAMPLE_COUNT);
	    vec3[2] importanceSampleResult = ImportanceSampleGGX(xi, roughness, sampleVector);
	    vec3 H = importanceSampleResult[0];
	    //H = hemisphereSample_uniform(xi.x, xi.y, normal);
	    
	    const bool USE_CONETRACING_FOR_DIFFUSE = false;
  		if(USE_CONETRACING_FOR_DIFFUSE) {
		  	TraceResult traceResult = traceCubes(positionWorld, H, v, roughness, metallic, color);
			vec4 sampleNearest = textureLod(probes, vec4(traceResult.dirToHitPointNearest, traceResult.probeIndexNearest), lod);
			if(traceResult.probeIndexSecondNearest == -1) {
				resultDiffuse.rgb += diffuseColor.rgb * sampleNearest.rgb;
				continue;
			}
			vec4 sampleSecondNearest = textureLod(probes, vec4(traceResult.dirToHitPointSecondNearest, traceResult.probeIndexSecondNearest), lod);
			
			resultDiffuse.rgb += diffuseColor * mix(sampleNearest, sampleSecondNearest, 1-sampleNearest.a).rgb;
    	} else {
			resultDiffuse.rgb += diffuseColor * textureLod(probes, vec4(H, index), lod).rgb * clamp(dot(normal, H), 0, 1);
    	}
	  }
	  resultDiffuse.rgb /= SAMPLE_COUNT;
  } else {
  	resultDiffuse.rgb = diffuseColor.rgb * textureLod(probes, vec4(projectedNormal, index), MAX_MIPMAPLEVEL-1).rgb;
  }
  
  result.diffuseColor = resultDiffuse.rgb;
  result.specularColor = resultSpecular.rgb;
  return result;
}

BoxIntersectionResult getTwoNearestProbeIndicesAndIntersectionsForPosition(TileProbes tileProbes, vec3 position, vec3 V, vec3 normal, vec2 uv) {
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
	
	//for(int i = 0; i < tileProbes.count; i++) {
	for(int i = 0; i < activeProbeCount; i++) {
		//int currentProbeIndex = tileProbes.indices[i];
		int currentProbeIndex = i;
		vec3 currentEnvironmentMapMin = environmentMapMin[currentProbeIndex];
		vec3 currentEnvironmentMapMax = environmentMapMax[currentProbeIndex];
		if(!isInside(position, currentEnvironmentMapMin, currentEnvironmentMapMax)) { continue; }
		vec3 distVectorHalf = vec3(distance(currentEnvironmentMapMin.x, currentEnvironmentMapMax.x), distance(currentEnvironmentMapMin.y, currentEnvironmentMapMax.y), distance(currentEnvironmentMapMin.z, currentEnvironmentMapMax.z))/2;
		vec3 currentCenter = currentEnvironmentMapMin + distVectorHalf;
		
		float currentDist = distance(currentCenter, position);
		if(currentDist < minDist1) {
			minDist2 = minDist1;
			iForNearest2 = iForNearest1;
			
			minDist1 = currentDist;
			iForNearest1 = currentProbeIndex;
		} else if(currentDist < minDist2){
			minDist2 = currentDist;
			iForNearest2 = currentProbeIndex;
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

ProbeSample getProbeColors(TileProbes tileProbes, vec3 positionWorld, vec3 V, vec3 normalWorld, float roughness, float metallic, vec2 uv, vec3 color) {
	ProbeSample result;
	
	float mipMapLevel = roughness * MAX_MIPMAPLEVEL;
	float mipMapLevelSecond = mipMapLevel;
	
	BoxIntersectionResult twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForPosition(tileProbes, positionWorld, V, normalWorld, uv);
	int probeIndexNearest = twoIntersectionsAndIndices.indexNearest;
	int probeIndexSecondNearest = twoIntersectionsAndIndices.indexSecondNearest;
	vec3 intersectionNearest = twoIntersectionsAndIndices.intersectionNormalNearest;
	vec3 intersectionSecondNearest = twoIntersectionsAndIndices.intersectionNormalSecondNearest;
	
	vec3 normal = normalize(normalWorld);
	vec3 reflected = normalize(reflect(V, normalWorld));
	vec3 boxProjectedRefractedNearest = boxProjection(positionWorld, refract(V, normalWorld, 1), 0);
	
	float mixer = calculateWeight(positionWorld, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest],
									 environmentMapMin[probeIndexSecondNearest], environmentMapMax[probeIndexSecondNearest]);
	
	bool onlyFirstProbeFound = (probeIndexNearest != -1 && probeIndexSecondNearest == -1) || (probeIndexNearest != -1 && probeIndexNearest == probeIndexSecondNearest);
	bool noProbeFound = probeIndexNearest == -1 && probeIndexSecondNearest == -1;
	
	// early out
	if(onlyFirstProbeFound) {
		result = importanceSampleProjectedCubeMap(probeIndexNearest, positionWorld, normal, reflected, V, roughness, metallic, color);
		//result.refractedColor = textureLod(probes, vec4(boxProjectedRefractedNearest, probeIndexNearest), roughness).rgb;
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
	
	//result.refractedColor = textureLod(probes, vec4(boxProjectedRefractedNearest, probeIndexNearest), roughness).rgb;
	return result;
}

shared int currentProbeIndex = 0;
shared int currentArrayIndex = 0;
shared int[10] probeIndicesForTile;

shared uint minDepth = 0xFFFFFFFF;
shared uint maxDepth = 0;

TileProbes getProbesForTile(int in_count, int[10] in_indices) {
	TileProbes result;
	result.count = in_count;
	result.indices = in_indices;
	return result;
}

void main()
{
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	
	vec2 st = vec2(storePos) / vec2(screenWidth, screenHeight);
	vec4 colorMetallic = textureLod(diffuseMap, st, 0);
	vec3 color = colorMetallic.rgb;
	vec4 positionViewRoughness = textureLod(positionMap, st, 0);
	vec3 positionView = positionViewRoughness.rgb;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec4 normalViewDepth = textureLod(normalMap, st, 0);
	vec3 normalView = normalViewDepth.rgb;
	uint depth = uint(normalViewDepth.a * 0xFFFFFFFF);
  	vec3 normalWorld = normalize(inverse(viewMatrix) * vec4(normalView,0)).xyz;
  	float roughness = positionViewRoughness.a;
	float metallic = colorMetallic.a;
  	vec4 position_clip = (projectionMatrix * viewMatrix * vec4(positionWorld,1));
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	V = normalize(inverse(viewMatrix) * vec4(positionView,0)).xyz;
	
	atomicMin(minDepth, depth);
	atomicMax(maxDepth, depth);
	barrier();
	float minDepthZ = float(minDepth / float(0xFFFFFFFF));
    float maxDepthZ = float(maxDepth / float(0xFFFFFFFF));
    mat4 inverseViewProjection = inverse(projectionMatrix * viewMatrix);
    mat4 inverseView = inverse(viewMatrix);
    mat4 inverseProjection = inverse(projectionMatrix);
    
    vec4 screenSpacePosition = vec4(st * 2.0 - 1.0, normalViewDepth.a, 1);
	vec4 worldSpacePosition = inverseProjection * screenSpacePosition;
	worldSpacePosition /= worldSpacePosition.w;
	worldSpacePosition = inverseView * vec4(worldSpacePosition.xyz, 1);
	
    vec4 screenSpaceMin = vec4(st * 2.0 - 1.0, minDepthZ, 1);
	vec4 worldSpaceMin = inverseProjection * screenSpaceMin;
	worldSpaceMin /= worldSpaceMin.w;
	worldSpaceMin = inverseView * vec4(worldSpaceMin.xyz, 1);
    vec4 screenSpaceMax = vec4(st * 2.0 - 1.0, maxDepthZ, 1); // maxDepthZ * 2.0 - 1.0
	vec4 worldSpaceMax = inverseProjection * screenSpaceMax;
	worldSpaceMax /= worldSpaceMax.w;
	worldSpaceMax = inverseView * vec4(worldSpaceMax.xyz, 1);
	
	const vec3 bias = vec3(10,10,10);
    const bool useBattlefieldMethod = true;

	if(!useBattlefieldMethod) {
		uint threadCount = WORK_GROUP_SIZE * WORK_GROUP_SIZE;
	    uint passCount = (activeProbeCount + threadCount - 1) / threadCount;
		for (uint passIt = 0; passIt < passCount; ++passIt) {
		    uint probeIndex = passIt * threadCount + gl_LocalInvocationIndex;
		    probeIndex = min(probeIndex, activeProbeCount);
		    
		    if (probeIndex < 10) {
			    if( isInside(worldSpaceMin.xyz, environmentMapMin[probeIndex]-bias, environmentMapMax[probeIndex]+bias) ||
					isInside(worldSpaceMax.xyz, environmentMapMin[probeIndex]-bias, environmentMapMax[probeIndex]+bias))
				{
		            uint id = atomicAdd(currentArrayIndex, 1);
		            probeIndicesForTile[id] = int(probeIndex);
			    }
		    }
		}
	} else {
		for (int probeIndex = int(gl_LocalInvocationIndex); probeIndex < activeProbeCount; probeIndex += WORK_GROUP_SIZE) {
			if(probeIndex < activeProbeCount) {
				if( isInside(worldSpaceMin.xyz, environmentMapMin[probeIndex]-bias, environmentMapMax[probeIndex]+bias) ||
					isInside(worldSpaceMax.xyz, environmentMapMin[probeIndex]-bias, environmentMapMax[probeIndex]+bias)) {
				
					uint index = atomicAdd(currentArrayIndex, 1);
	            	probeIndicesForTile[index] = probeIndex;
				}
			}
		}
	}
	
	barrier();
	
	TileProbes probesForTile = getProbesForTile(currentArrayIndex, probeIndicesForTile);
	ProbeSample probeColorsDiffuseSpecular = getProbeColors(probesForTile, positionWorld, V, normalWorld, roughness, metallic, st, color);
	
	vec3 result = 0.5*(probeColorsDiffuseSpecular.diffuseColor + probeColorsDiffuseSpecular.specularColor);

	if(secondBounce) {
		TileProbes tileProbes;
		BoxIntersectionResult twoIntersectionsAndIndices = getTwoNearestProbeIndicesAndIntersectionsForPosition(tileProbes, positionWorld, V, normalWorld, st);
		int probeIndexNearest = twoIntersectionsAndIndices.indexNearest;
		vec3 intersectionNearest = twoIntersectionsAndIndices.intersectionNormalNearest;
		vec3 positionWorldSecondBounce = intersectionNearest;
		vec3 centerProbe = environmentMapMin[probeIndexNearest] + (environmentMapMax[probeIndexNearest] - environmentMapMin[probeIndexNearest])/2;
		vec3 normalWorldSeconBounce = normalize(centerProbe - intersectionNearest);
		vec3 viewWorldSecondBounce = -normalize(intersectionNearest - positionWorld);
		const float mip = MAX_MIPMAPLEVEL;
			
		const bool useConeTracingForSecondBounce = false;
		if(useConeTracingForSecondBounce) {
			vec3 tempResult;
			TraceResult temp = traceCubes(positionWorldSecondBounce, normalWorldSeconBounce, viewWorldSecondBounce, 1, 0, vec3(0.5,0.5,0.5));
			vec4 sampleNearest = texture(probes, vec4(temp.dirToHitPointNearest, temp.probeIndexNearest), mip);
			vec3 sampleSecondNearest = texture(probes, vec4(temp.dirToHitPointSecondNearest, temp.probeIndexSecondNearest), mip).rgb;
			if(temp.probeIndexSecondNearest == -1) {
				tempResult += sampleNearest.rgb;
			} else {
				tempResult += mix(sampleNearest.rgb, sampleSecondNearest, 1-sampleNearest.a);
			}
			result.rgb *= 0.5;
			result.rgb += 0.5*tempResult;
		} else {
			const bool useFullImportanceSampleForSecondBounce = true;
			
			if (useFullImportanceSampleForSecondBounce) {
				ProbeSample probeSample = importanceSampleProjectedCubeMap(currentProbe, positionWorldSecondBounce.xyz, normalWorldSeconBounce.xyz, reflect(viewWorldSecondBounce, normalWorldSeconBounce.xyz), viewWorldSecondBounce, 1, 0, color.rgb);
				vec3 sampleFromLastFrameAsSecondBounce = probeSample.diffuseColor + probeSample.specularColor;
				result.rgb *= 0.5;
				result.rgb += 0.5*sampleFromLastFrameAsSecondBounce;
			} else {
				vec3 boxProjectedNormal = boxProjection(positionWorldSecondBounce.xyz, normalWorldSeconBounce, probeIndexNearest);
				result.rgb *= 0.5;
				result.rgb += 0.5*texture(probes, vec4(boxProjectedNormal, currentProbe), mip).rgb; // TODO: ADJUST THIS WITH NdotL!!!!
			}
		}
	}
	
	
	vec3 mini = environmentMapMin[currentProbe];
	vec3 maxi = environmentMapMax[currentProbe];
	float probeVisibility = isInside(positionWorld.xyz, mini, maxi) ? 1 : 0;
	
	// Since the other lights are rendered additively, we have to take the current sample and blend by ourselves
	vec4 oldSample = imageLoad(out_environment, storePos);
	imageStore(out_environment, storePos, vec4(result + oldSample.rgb, probeVisibility));
	
	/*
	if(localIndex.x == 0 || localIndex.y == 0) {
		imageStore(out_environment, storePos, vec4(1, 0, 0, 1));
	}
	if(probesForTile.count == 0) {
		imageStore(out_environment, storePos, vec4(1, 0, 0, 1));
	} else if(probesForTile.count == 1) {
		imageStore(out_environment, storePos, vec4(0, 1, 0, 1));
	} else if(probesForTile.count == 2) {
		imageStore(out_environment, storePos, vec4(0, 0, 1, 1));
	} else if(probesForTile.count == 3) {
		imageStore(out_environment, storePos, vec4(1, 0, 1, 1));
	} else if(probesForTile.count > 3) {
		imageStore(out_environment, storePos, vec4(1, 1, 1, 1));
	}*/
}