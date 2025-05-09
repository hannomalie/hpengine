layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=6) uniform sampler2D shadowMap;

layout(binding=8) uniform samplerCubeArray probes;

layout(binding=9) uniform sampler2D shadowMapArealight0;
layout(binding=10) uniform sampler2D shadowMapArealight1;
layout(binding=11) uniform sampler2D shadowMapArealight2;
layout(binding=12) uniform sampler2D shadowMapArealight3;
layout(binding=13) uniform sampler2D shadowMapArealight4;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};


const float pointLightRadius = 5.0;

uniform bool firstBounceForProbe;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightAmbient;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec3 tangent_world;
in vec3 bitangent_world;
in vec4 position_clip;
in vec4 position_clip_last;
//in vec4 position_clip_uv;
//in vec4 position_clip_shadow;
in vec4 position_world;


in vec3 eyeVec;
in vec3 eyePos_world;
in mat3 TBN;
flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;
flat in int outEntityBufferIndex;
flat in int outMaterialIndex;

const int pointLightMaxCount = 40;
uniform int activePointLightCount = 0;

//include(globals_structs.glsl)
layout(std430, binding=5) buffer _pointLights {
	PointLight pointLights[100];
};
layout(std430, binding=6) buffer _areaLights {
	AreaLight areaLights[100];
};

const int areaLightMaxCount = 2;
uniform int activeAreaLightCount = 0;
uniform mat4[areaLightMaxCount] areaLightShadowMatrices;

uniform vec3 probePosition;
uniform vec3 probeSize;
uniform int probeIndex = 0;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

layout(location=0)out vec4 out_color;

const float PI = 3.1415926536;
const float MAX_MIPMAPLEVEL = 8;//11; HEMISPHERE is half the cubemap

mat3 cotangent_frame( vec3 N, vec3 p, vec2 uv )
{
	vec3 dp1 = dFdx( p );
	vec3 dp2 = dFdy( p );
	vec2 duv1 = dFdx( uv );
	vec2 duv2 = dFdy( uv );

	vec3 dp2perp = cross( dp2, N );
	vec3 dp1perp = cross( N, dp1 );
	vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;

	float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
	return mat3( T * invmax, B * invmax, N );
}
vec3 perturb_normal(vec3 N, vec3 V, vec2 texcoord, sampler2D normalMap)
{
	vec3 map = (texture(normalMap, texcoord)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}

float linearizeDepth(float z)
{
  float n = 0.1; // camera z near TODO: MAKE THIS UNIFORRRRRRRMMMM
  float f = 500; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));
}
#define kPI 3.1415926536f
vec3 decode(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}
vec2 encode(vec3 n) {
	//n = vec3(n*0.5+0.5);
    return (vec2((atan(n.x, n.y)/kPI), n.z)+vec2(1,1))*0.5;
}

vec3 projectOnPlane(in vec3 p, in vec3 pc, in vec3 pn)
{
    float distance = dot(pn, p-pc);
    return p - distance*pn;
}
int sideOfPlane(in vec3 p, in vec3 pc, in vec3 pn){
   if (dot(p-pc,pn)>=0.0) return 1; else return 0;
}
vec3 linePlaneIntersect(in vec3 lp, in vec3 lv, in vec3 pc, in vec3 pn){
   return lp+lv*(dot(pn,pc-lp)/dot(pn,lv));
}
vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return vec3(0,0,0);
	}
	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = texture2D(shadowMap,ShadowCoordPostW.xy);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;
	//moments = blur(shadowMap, ShadowCoordPostW.xy, moments.y).rg;
	//moments = textureLod(shadowMap, ShadowCoordPostW.xy, 0).rg;
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
	//float p_max = (variance / (variance + d*d));
	// thanks, for light bleeding reduction, FOOGYWOO! http://dontnormalize.me/
	float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));

	return vec3(p_max,p_max,p_max);
}

vec3 cookTorrance(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-position);
	V = ViewVector;
 	vec3 L = -normalize((vec4(lightDirection, 0)).xyz);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);

	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	float alpha = roughness*roughness;
	float alphaSquare = alpha*alpha;
	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float denom = (NdotH*NdotH*(alphaSquare-1))+1;
	float D = alphaSquare/(3.1416*denom*denom);

	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));

    // Schlick
    float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2, electrics up to 1 and mostly above 0.5
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(L, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;

	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	diff = (diff.rgb) * (1-F0);

	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	return diff + lightDiffuse.rgb * specularColor * cookTorrance;
}

float calculateAttenuation(float dist, float lightRadius) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}

sampler2D getAreaLightShadowmapSampler(int index) {
	if(index == 0) {
		return shadowMapArealight0;
	} else if(index == 1) {
		return shadowMapArealight1;
	} else if(index == 2) {
		return shadowMapArealight2;
	} else if(index == 3) {
		return shadowMapArealight3;
	} else {
		return shadowMapArealight4;
	}
}

vec3 getVisibility(float dist, vec4 ShadowCoordPostW, vec2 texCoords, int index)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return vec3(0,0,0);
	}

	vec4 shadowMapSample = texture2D(getAreaLightShadowmapSampler(index), ShadowCoordPostW.xy);
	vec2 moments = shadowMapSample.rg;

	float bias = 0.003;
	if (dist <= moments.x + bias) {
		return vec3(1.0,1.0,1.0);
	} else { return vec3(0,0,0); }
}

vec3 cookTorranceAreaLight(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDiffuse,
							   vec3 lightPosition, int index, vec3 diffuseColor, vec3 specularColor) {

    AreaLight light = areaLights[index];
//http://renderman.pixar.com/view/cook-torrance-shader
	vec3 V = normalize(-position);
	V = ViewVector;
	vec3 lightViewDirection = (light.modelMatrix * vec4(0,0,1,0)).xyz;
	vec3 lightUpDirection = (light.modelMatrix * vec4(0,1,0,0)).xyz;
	vec3 lightRightDirection = (light.modelMatrix * vec4(1,0,0,0)).xyz;
	float lightWidth = light.width;
	float lightHeight = light.height;
	float lightRange = light.range;

	vec3 lVector[ 4 ];
	vec3 leftUpper = lightPosition + (lightWidth/2)*(-lightRightDirection) + (lightHeight/2)*(lightUpDirection);
	vec3 rightUpper = lightPosition + (lightWidth/2)*(lightRightDirection) + (lightHeight/2)*(lightUpDirection);
	vec3 leftBottom = lightPosition + (lightWidth/2)*(-lightRightDirection) + (lightHeight/2)*(-lightUpDirection);
	vec3 rightBottom = lightPosition + (lightWidth/2)*(lightRightDirection) + (lightHeight/2)*(-lightUpDirection);

	lVector[0] = normalize(leftUpper - position);
	lVector[1] = normalize(rightUpper - position);
	lVector[3] = normalize(leftBottom - position);
	lVector[2] = normalize(rightBottom - position); // clockwise oriented all the lights rectangle points
	float tmp = dot( lVector[ 0 ], cross( ( leftBottom - leftUpper ).xyz, ( rightUpper - leftUpper ).xyz ) );
	if ( tmp > 0.0 ) {
		return vec3(0,0,0);
	} else {
		vec3 lightVec = vec3( 0.0 );
		for( int i = 0; i < 4; i ++ ) {

			vec3 v0 = lVector[ i ];
			vec3 v1 = lVector[ int( mod( float( i + 1 ), float( 4 ) ) ) ]; // ugh...
			lightVec += acos( dot( v0, v1 ) ) * normalize( cross( v0, v1 ) );

		}

	 	vec3 L = lightVec;
	    vec3 H = normalize(L + V);
    	vec3 N = normal;
	    vec3 P = position;
        vec3 R = reflect(V, N);
        vec3 E = linePlaneIntersect(position, R, lightPosition, lightViewDirection);

		float width = lightWidth;
	    float height = lightHeight;
	    vec3 projection = projectOnPlane(position, lightPosition, lightViewDirection);
	    vec3 dir = projection-lightPosition;
	    vec2 diagonal = vec2(dot(dir,lightRightDirection),dot(dir,lightUpDirection));
	    vec2 nearest2D = vec2(clamp(diagonal.x, -width, width),clamp(diagonal.y, -height, height));

	    // this is the amount of space the projected point is away from the border of the area light.
	    // if the term is positive, the trace is besides the broder. Up to 5 units linear fading the specular, so that
	    // we have glossy specular on rough surfaces
	    vec2 overheadVec2 = (vec2(abs(diagonal.x)-width, abs(diagonal.y)-height) / 5);
	    float overhead = clamp(max(overheadVec2.x, overheadVec2.y), 0.0, 1.0);

	    vec3 nearestPointInside = lightPosition + (lightRightDirection * nearest2D.x + lightUpDirection * nearest2D.y);
	    //if(distance(P, nearestPointInside) > lightRange) { discard; }
	    vec2 texCoords = nearest2D / vec2(width, height);
        texCoords += 1;
	    texCoords /=2;
        float mipMap = (2*(distance(texCoords,vec2(0.5,0.5)))) * 7;

		float NdotL = max(dot(N, L), 0.0);
	    float NdotH = max(dot(N, H), 0.0);
	    float NdotV = max(dot(N, V), 0.0);
    	float VdotH = max(dot(V, H), 0.0);

		// irradiance factor at point
		float factor = NdotL / ( 2.0 * 3.14159265 );
		// frag color
		vec3 diffuse = 2*lightDiffuse * factor;

		float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));

		float alpha = acos(NdotH);
		// GGX
		//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
		float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));

	    // Schlick
		float F0 = 0.02;
		// Specular in the range of 0.02 - 0.2
		// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
		float glossiness = (1-roughness);
		float maxSpecular = mix(0.2, 1.0, metallic);
		F0 = max(F0, (glossiness*maxSpecular));
		//F0 = max(F0, metallic*0.2);
	    float fresnel = 1; fresnel -= dot(V, H);
		fresnel = pow(fresnel, 5.0);
		float temp = 1.0; temp -= F0;
		fresnel *= temp;
		float F = fresnel + F0;

		vec4 positionShadow = (areaLightShadowMatrices[index] * vec4(position, 1));
	  	positionShadow.xyz /= positionShadow.w;
	  	float depthInLightSpace = positionShadow.z;
	    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
		vec3 visibility = getVisibility(depthInLightSpace, positionShadow, texCoords, index);

		//diffuse = diffuse * (1-fresnel);

        float specular = clamp(F*D*G/(4*(NdotL*NdotV)), 0.0, 1.0);

		return visibility * diffuse * diffuseColor + diffuse * specularColor * specular * clamp(length(overheadVec2), 0.0, 1.0);
	}
}
vec3 cookTorrancePointLight(in vec3 ViewVector, in vec3 position, in vec3 normal, float roughness, float metallic, vec3 lightDiffuse,
							   vec3 lightPosition, float lightRadius, float dist, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(-position);
	V = ViewVector;

	// make every pointlight a sphere light for better highlights, thanks Unreal Engine
 	vec3 r = reflect(V, normal);
    vec3 centerToRay = (lightPosition - position) * clamp(dot((lightPosition - position), r),0,1) * r;
    vec3 light_position = (lightPosition) + centerToRay*clamp(pointLightRadius/length(centerToRay),0,1);

 	vec3 L = normalize(light_position-position);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normal);
    vec3 P = position;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);

	float alpha = acos(NdotH);
	float alphaSquare = alpha*alpha;
	// adjust distribution for sphere light
	alpha = clamp(alpha+(pointLightRadius/(3*dist)),0,1);

	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float D = (alpha*alpha)/(3.1416*pow(((NdotH*NdotH*((alpha*alpha)-1))+1), 2));

	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));

    // Schlick
	float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(V, H);
	fresnel = pow(fresnel, 5.0);
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;

	//float specularAdjust = length(lightDiffuse)/length(vec3(1,1,1));
	vec3 diff = vec3(lightDiffuse.rgb) * NdotL;
	diff = (diff.rgb) * diffuseColor;


	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);
	float attenuation = calculateAttenuation(dist, lightRadius);

	return attenuation*(diff + lightDiffuse.rgb * specularColor * cookTorrance);
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
vec3 boxProjection(vec3 position_world, vec3 texCoords3d, int probeIndex) {
	vec3 environmentMapMin = environmentMapMin[probeIndex];
	vec3 environmentMapMax = environmentMapMax[probeIndex];
	vec3 posonbox = getIntersectionPoint(position_world, texCoords3d, environmentMapMin, environmentMapMax);

	//vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	vec3 environmentMapWorldPosition = environmentMapMin + (environmentMapMax - environmentMapMin)/2.0;

	return normalize(posonbox - environmentMapWorldPosition.xyz);
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

struct ProbeSample {
	vec3 diffuseColor;
	vec3 specularColor;
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
float p(vec2 spherical_coords, float roughness) {
	float a = roughness*roughness;
	float a2 = a*a;

	float result = (a2 * cos(spherical_coords.x) * sin(spherical_coords.x)) /
					(PI * pow((pow(cos(spherical_coords.x), 2) * (a2 - 1)) + 1, 2));
	return result;
}
vec3[2] ImportanceSampleGGX( vec2 Xi, float Roughness, vec3 N ) {
	float a = Roughness * Roughness;
	float Phi = 2 * PI * Xi.x;
	float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )+0.000001) );
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

ProbeSample importanceSampleProjectedCubeMap(int index, vec3 positionWorld, vec3 normal, vec3 reflected, vec3 v, float roughness, float metallic, vec3 color) {
  vec3 diffuseColor = mix(color, vec3(0.0,0.0,0.0), metallic);
  vec3 SpecularColor = mix(vec3(0.04,0.04,0.04), color, metallic);

  ProbeSample result;
  //result.diffuseColor = textureLod(probes, vec4(boxProjection(positionWorld, reflected, index), index), 0).rgb;
  //return result;

  vec3 V = v;
  vec3 n = normal;
  vec3 R = reflected;
  const int N = 4;
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

void main()
{

    int entityIndex = outEntityBufferIndex;
    Entity entity = outEntity;

	Material material = outMaterial;

    vec3 materialDiffuseColor = vec3(material.diffuseR,
                                     material.diffuseG,
                                     material.diffuseB);
    float materialRoughness = float(material.roughness);
    float materialMetallic = float(material.metallic);
    float materialAmbient = float(material.ambient);
    float parallaxBias = float(material.parallaxBias);
    float parallaxScale = float(material.parallaxScale);
    float materialTransparency = float(material.transparency);


	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	//V = normalize((eyePos_world.xyz - position_world.xyz).xyz);
	vec2 UV = texCoord;


	vec4 color = vec4(materialDiffuseColor, 1);
    float alpha = materialTransparency;
    if(material.handleDiffuse > 0) {
        sampler2D _diffuseMap = sampler2D((material.handleDiffuse));

    	color = texture(_diffuseMap, UV);
        alpha *= color.a;
        if(color.a<0.1)
        {
            discard;
        }
    }

	float depth = (position_clip.z / position_clip.w);
    //out_color = vec4(color.rgb, depth);
    //out_color.a = 1;
    vec3 vecToPoint = (position_world.xyz - probePosition);
    vec3 overhead = abs(vecToPoint) - probeSize/2;
    const vec3 bias = vec3(5,5,5);
    overhead -= bias;
    bvec3 hasOverhead = greaterThan(overhead, vec3(0,0,0));
    out_color.a = depth;
    out_color.a = any(hasOverhead)? 0 : 1;

    vec3 diffuseColor = mix(color.rgb, vec3(0,0,0), materialMetallic); // biased, since specular term is only valid at POI of the probe...mh
    vec3 specularColor = mix(vec3(0.04,0.04,0.04), color.rgb, materialMetallic);

	vec3 PN_world = normalize(normal_world);
	vec3 old_PN_world = PN_world;
    if(material.handleNormal != 0) {
        sampler2D _normalMap = sampler2D((material.handleNormal));
        PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
    }

	/////////////////// SHADOWMAP
	vec3 visibility = vec3(1,1,1);
	vec4 positionShadow = (shadowMatrix * vec4(position_world.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = chebyshevUpperBound(depthInLightSpace, positionShadow);
	/////////////////// SHADOWMAP

	vec3 lightDiffuseSpecular = cookTorrance(V, position_world.xyz, PN_world.xyz, materialRoughness, materialMetallic, diffuseColor, specularColor);

	// since probes are used for ambient lighting, but don't receive ambient, they have to be biased with some ambient light
	/*float quarterAmbientStrength = 0.05;
	out_color.rgb = quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(-lightDirection.x,-lightDirection.y,lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(lightDirection.x,-lightDirection.y,lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * lightDiffuse.rgb * color.rgb * clamp(dot(vec3(lightDirection.x,lightDirection.y,-lightDirection.z), PN_world), 0.0, 1.0);
	out_color.rgb += quarterAmbientStrength * color.rgb * clamp(dot(lightDirection, PN_world), 0.0, 1.0);*/

	vec3 directLight = diffuseColor.rgb * lightDiffuseSpecular.rgb * visibility;

	out_color.rgb = directLight.rgb;

	for(int i = 0; i < min(pointLightMaxCount, activePointLightCount); i++) {
		PointLight light = pointLights[i];
		vec3 lightPosition = vec3(float(light.positionX), float(light.positionY), float(light.positionZ));
		float dist = distance(position_world.xyz, lightPosition);
		if(dist > float(light.radius)) { continue; }

		out_color.rgb += cookTorrancePointLight(-V, position_world.xyz, PN_world.xyz, materialRoughness, materialMetallic, vec3(float(light.colorR), float(light.colorG), float(light.colorB)), lightPosition, float(light.radius), dist, diffuseColor, specularColor);
	}

	for(int i = 0; i < min(areaLightMaxCount, activeAreaLightCount); i++) {
	    AreaLight light = areaLights[i];
		vec3 lightPosition = (light.modelMatrix * vec4(0,0,0,1)).xyz;
		out_color.rgb += cookTorranceAreaLight(-V, position_world.xyz, PN_world.xyz, materialRoughness, materialMetallic, light.color.rgb, lightPosition, i, diffuseColor, specularColor);
	}

	if(firstBounceForProbe) {
		vec3 boxProjectedNormal = boxProjection(position_world.xyz, PN_world, probeIndex);
		vec3 sampleFromLastFrameAsSecondBounce = textureLod(probes, vec4(boxProjectedNormal, probeIndex), 1 + 8*materialRoughness).rgb;
		ProbeSample probeSample = importanceSampleProjectedCubeMap(probeIndex, position_world.xyz, PN_world.xyz, reflect(-V, PN_world.xyz), -V, materialRoughness, materialMetallic, color.rgb);
		sampleFromLastFrameAsSecondBounce = probeSample.diffuseColor + probeSample.specularColor;
		out_color.rgb = mix(out_color.rgb*4, sampleFromLastFrameAsSecondBounce, 0.5);
	}

	// Fake the other missing bounces with some ambient light...
	out_color.rgb += 0.125 * color.rgb;

//    out_color.rgb = vec3(1);
	//out_color.rgb = PN_world;
	//out_color.rgb = vec3(metallic,metallic,metallic);
//	out_color = vec4(color.rgb,1);
}
