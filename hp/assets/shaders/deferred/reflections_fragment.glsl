#version 420

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform sampler2D lightAccumulationMap; // diffuse, specular

layout(binding=6) uniform samplerCube globalEnvironmentMap;
layout(binding=8) uniform samplerCubeArray probes;

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


struct ProbeSample {
	vec3 diffuseColor;
	vec3 specularColor;
};

float rand(vec2 co){
	return 0.5+(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453))*0.5;
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
		return max(extents.y, extents.z) / 512; // TODO: NO HARDCODED RESOLUTION VALUES
	} else if(mainAxis.y > 0) {
		return max(extents.x, extents.z) / 512;
	} else {
		return max(extents.x, extents.y) / 512;
	}
}

ProbeSample importanceSampleProjectedCubeMap(int index, vec3 positionWorld, vec3 normal, vec3 reflected, vec3 v, float roughness, float metallic, vec3 color) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 SpecularColor = mix(vec3(0.04,0.04,0.04), color, metallic);
  
  ProbeSample result;
  //result.diffuseColor = textureLod(probes, vec4(boxProjection(positionWorld, reflected, index), index), 0).rgb;
  //return result;
  
  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int N = 32;
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
	    //lod = MAX_MIPMAPLEVEL * pdf;
	    //lod = clamp(lod, 0, MAX_MIPMAPLEVEL);
	    //lod *= MAX_MIPMAPLEVEL;
	    //lod *= distToIntersection*roughness;
	    //lod = roughness * (1+roughness) * MAX_MIPMAPLEVEL;
	    //lod *= pow(1+clamp(distToIntersection/500.0, 0, 1), 4);
	    
    	vec4 SampleColor = textureLod(probes, vec4(H, index), lod);
    	
		vec3 cookTorrance = SpecularColor * SampleColor.rgb * clamp((F*G/(4*(NoL*NoV))), 0.0, 1.0);
		ks += fresnel;
		resultSpecular.rgb += clamp(cookTorrance, vec3(0,0,0), vec3(1,1,1));
	}
  }
  
  if(pdfSum < 0.9){
  	result.diffuseColor = vec3(1,0,0);
  	//return result;
  }
  
  resultSpecular = resultSpecular/(N);
  //resultDiffuse = resultDiffuse/(N);
  ks = clamp(ks/N, 0, 1);
  float kd = (1 - ks) * (1 - metallic);
  
  normal = boxProjection(positionWorld, normal, index);
  resultDiffuse.rgb = diffuseColor * textureLod(probes, vec4(normal, index), MAX_MIPMAPLEVEL).rgb;
  
  result.diffuseColor = resultDiffuse.rgb;
  result.specularColor = resultSpecular.rgb;
  return result;
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

vec3 rayCast(vec3 color, vec3 probeColor, vec2 screenPos, vec3 targetPosView, vec3 targetNormalView, float roughness) {

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
				vec3 targetPositionWorld = (inverse(viewMatrix) * vec4(targetPosView,1)).xyz;
				vec3 targetNormalWorld = (inverse(viewMatrix) * vec4(targetNormalView,0)).xyz;
				
				float distanceInWorld = distance(currentPosSample, targetPositionWorld);
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
    			
    			vec4 lightDiffuseSpecular = textureLod(lightAccumulationMap, resultCoords.xy, mipMapChoser);
    			vec3 reflectedColor = lightDiffuseSpecular.rgb;
    			
    			vec3 lightDirection = currentPosSample - targetPositionWorld;
    			
				return mix(probeColor, reflectedColor, 1-screenEdgefactor);
		  	}
		  	//return vec3(1,0,0);
		  	//color = texture(environmentMap, normalize(normalize((inverse(viewMatrix) * vec4(reflectionVecView,0)).xyz))).rgb;
			return probeColor;
		  }
	}
	
	return probeColor;
}

bool isInside(vec3 position, vec3 minPosition, vec3 maxPosition) {
	return(all(greaterThanEqual(position, minPosition)) && all(lessThanEqual(position, maxPosition))); 
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
	
	if(USE_CACHED_RPROBES) {
		ivec2 precalculatedIndices = ivec2(texture(motionMap, uv).ba);
		if(precalculatedIndices.x != -1) {
			vec3 mini = environmentMapMin[precalculatedIndices.x];
			vec3 maxi = environmentMapMax[precalculatedIndices.x];
			result.indexNearest = precalculatedIndices.x;
			result.intersectionNormalNearest = getIntersectionPoint(position, normal, mini, maxi);
			result.intersectionReflectedNearest = getIntersectionPoint(position, reflectionVector, mini, maxi);
	
			if(precalculatedIndices.y != -1) {
				result.indexSecondNearest = precalculatedIndices.y;
				result.intersectionNormalSecondNearest = getIntersectionPoint(position, normal, environmentMapMin[int(precalculatedIndices.y)], environmentMapMax[int(precalculatedIndices.y)]);
				result.intersectionReflectedSecondNearest = getIntersectionPoint(position, reflectionVector, environmentMapMin[int(precalculatedIndices.y)], environmentMapMax[int(precalculatedIndices.y)]);
				return result;
			} else if(NO_INTERPOLATION_IF_ONE_PROBE_CACHED) {
				return result;
			}
		}
	}	

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
	
	vec3 normal = normalize(normalWorld);
	vec3 reflected = normalize(reflect(V, normalWorld));
	
	float mixer = calculateWeight(positionWorld, environmentMapMin[probeIndexNearest], environmentMapMax[probeIndexNearest],
									 environmentMapMin[probeIndexSecondNearest], environmentMapMax[probeIndexSecondNearest]);
	
	bool onlyFirstProbeFound = (probeIndexNearest != -1 && probeIndexSecondNearest == -1) || (probeIndexNearest != -1 && probeIndexNearest == probeIndexSecondNearest);
	bool noProbeFound = probeIndexNearest == -1 && probeIndexSecondNearest == -1;
	
	// early out
	if(onlyFirstProbeFound) {
		result = importanceSampleProjectedCubeMap(probeIndexNearest, positionWorld, normal, reflected, V, roughness, metallic, color);
		//result.diffuseColor += SECONDBOUNCEFACTOR*textureLod(probes, vec4(vec3(1,0,0), probeIndexNearest), MAX_MIPMAPLEVEL+1).rgb; 
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
	
	//out_diffuseEnvironment.rgb = probeColorsDiffuseSpecular[0];
	out_diffuseEnvironment.a = getAmbientOcclusion(st);
	
	/*if(roughness < 0.2) {
		vec3 tempSSLR = rayCast(color, out_specularEnvironment.rgb, st, positionView, normalView.rgb, roughness);
		probeColorsDiffuseSpecular.specularColor = tempSSLR;
	}*/
	
	out_specularEnvironment.rgb = probeColorsDiffuseSpecular.diffuseColor + probeColorsDiffuseSpecular.specularColor;
}