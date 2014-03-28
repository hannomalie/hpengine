#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D shadowMap;
layout(binding=6) uniform sampler2D depthMap;

uniform bool useParallax;
uniform bool useSteepParallax;

uniform bool hasDiffuseMap;
uniform bool hasNormalMap;
uniform bool hasSpecularMap;
uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;
uniform float specularMapWidth = 1;
uniform float specularMapHeight = 1;
uniform float normalMapWidth = 1;
uniform float normalMapHeight = 1;

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec4 position_clip;
in vec4 position_clip_uv;
in vec4 position_clip_shadow;
in vec4 position_world;
in vec3 view_up;
in vec3 view_back;

in vec3 lightVec;
in vec3 halfVec;
in vec3 eyeVec;

out vec4 outColor;

float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));

vec2 poissonDisk[4] = vec2[](
  vec2( -0.94201624, -0.39906216 ),
  vec2( 0.94558609, -0.76890725 ),
  vec2( -0.094184101, -0.92938870 ),
  vec2( 0.34495938, 0.29387760 )
);

float random(vec4 seed4) {
	float dot_product = dot(seed4, vec4(12.9898,78.233,45.164,94.673));
    return fract(sin(dot_product) * 43758.5453);
}

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
vec3 perturb_normal( vec3 N, vec3 V, vec2 texcoord )
{
	vec3 map = (texture2D( normalMap, texcoord )).xyz;
	mat3 TBN = cotangent_frame( N, -V, texcoord );
	return normalize( TBN * map );
}
float linstep(float low, float high, float v){
    return clamp((v-low)/(high-low), 0.0, 1.0);
}
const float epsilon = 0.0025;
float eval_shadow (vec2 texcoods) {
	float shadow = texture (shadowMap, texcoods).r;
	if (shadow + epsilon < position_clip_shadow.z) {
		return 0.2; // shadowed
	}
	return 1.0; // not shadowed
}

float eval_shadow_poisson (vec2 texcoods) {
	float shadow = 1.0;
	for (int i=0;i<4;i++){
		float mapSample = texture(shadowMap, texcoods + poissonDisk[i]/700).r;
		if (mapSample + epsilon < position_clip_shadow.z) {
			shadow -= 0.2;
		}
	}
	return shadow; // not shadowed
}
float chebyshevUpperBound( float distance, vec2 ShadowCoordPostW) {
	vec2 moments = texture2D(shadowMap,ShadowCoordPostW.xy).rg;
	
	// Surface is fully lit. as the current fragment is before the light occluder
	if (distance <= moments.x) {
		return 1.0 ;
	}

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.00002);
	
	float d = distance - moments.x;
	float p_max = variance / (variance + d*d);

	return p_max;
}
float VSM(vec2 uv, float compare){
    vec2 moments = texture2D(shadowMap, uv).xy;
    float p = smoothstep(compare-0.02, compare, moments.x);
    float variance = max(moments.y - moments.x*moments.x, -0.001);
    float d = compare - moments.x;
    float p_max = linstep(0.2, 1.0, variance / (variance + d*d));
    return clamp(max(p, p_max), 0.0, 1.0);
}
void main(void) {

	vec2 UV;
	UV.x = texCoord.x * diffuseMapWidth;
	UV.y = texCoord.y * diffuseMapHeight;
	
	const vec4 diffuseLight = vec4(1,1,1,1);
	const vec4 ambientLight = vec4(0.11, 0.1, 0.1, 0);
	const vec4 specularLight = vec4(0.1, 0.1, 0.1, 0);
	
	vec4 diffuseMaterial = vec4(0.5,0.5,0.5,1);
	vec3 L = normalize(lightVec);
	vec3 V = normalize(eyeVec);
	
	if (useParallax) {
		float height = texture2D(heightMap, UV).r;
		float v = height * 0.106 - 0.012;
		UV = UV + (normalize(eyeVec).xy * v);
	} else if (useSteepParallax) {
		float n = 30;
		float bumpScale = 15;
		float step = 1/n;
		vec2 dt = V.xy * bumpScale / (n * V.z);
		
		float height = 1;
		vec2 t = UV;
		vec4 nb = texture2D(heightMap, t);
		while (nb.a < height) { 
			height -= step;
			t += dt; 
			nb = texture2D(heightMap, t); 
		}
		UV = t;
	}
	
	// DIFFUSE
	if (hasDiffuseMap) {
		diffuseMaterial = texture2D(diffuseMap, UV);
	}
	
	// NORMAL
	vec3 N = normalize(normal_model);
	vec3 PN = N;
	if (hasNormalMap) {
		PN = perturb_normal(N, V, UV);
	}
	//normal.y = -normal.y;
	
	// SPECULAR
	float specularStrength = 0.5;
	if (hasSpecularMap) {
		specularStrength = texture2D(specularMap, UV).r;
	}
	
	// DEPTH
	float depth = texture2D(depthMap, position_clip_uv.xy).r;
	
	{
		outColor = ambientLight * diffuseMaterial;
		
		// LIGHTING
		float lambertTerm = dot(PN, L);
		if (lambertTerm > 0.0)
		{
			float shininess = 1;
			vec3 E = V;
			vec3 R = reflect(-L, PN);
			float specular = pow( max(dot(R, E), 0.0), shininess);
			outColor += diffuseMaterial * diffuseLight * lambertTerm;
			outColor += specularStrength * specularLight * shininess;
		}
		
		float visibility = 1;
		if (true) {
			//visibility = eval_shadow_poisson(position_clip_shadow.xy);
			visibility = eval_shadow(position_clip_shadow.xy);
			//visibility = chebyshevUpperBound((position_clip_shadow.z), position_clip_shadow.xy);
			//visibility = VSM(position_clip_shadow.xy, position_clip_shadow.z);
		}
		
		// AMBIENT OCCLUSION
		float ao = 1;
		if (false) {
			vec3 fres = normalize(rand(diffuseMaterial.rg)*2) - vec3(1.0, 1.0, 1.0);
			vec3 ep = vec3(position_clip_uv.xy, depth);
			float rad = 0.006;
			float radD = rad/depth;
			float bl = 0.0f;
			float occluderDepth, depthDifference, normDiff;
			float falloff = 0.000002;
			float totStrength = 1.38;
			float strength = 0.07;
			float samples = 9;
			float invSamples = 1/samples;
			
			for(int i=0; i<samples;++i) {
			  vec3 ray = radD*reflect(pSphere[i],fres);
			  vec3 se = ep + sign(dot(ray,N) )*ray;
			  vec4 occluderFragment = texture2D(depthMap,se.xy).xyzw;
			  vec3 occNorm = occluderFragment.yzw;
			
			  // Wenn die Diff der beiden Punkte negativ ist, ist der Verdecker hinter dem aktuellen Bildpunkt
			  depthDifference = depth-occluderFragment.r;
			
			  // Berechne die Differenz der beiden Normalen als ein Gewicht
			  normDiff = (1.0-dot(occNorm,N));
			
			  // the falloff equation, starts at falloff and is kind of 1/x^2 falling
			  bl += step(falloff,depthDifference)*normDiff*(1.0-smoothstep(falloff,strength,depthDifference));
		    }
			ao = 1.0-totStrength*bl*invSamples;
		}
		
		outColor += (diffuseMaterial*ambientLight) - (1-ao);
		outColor *= visibility;
		outColor.a = 1;
		
		//outColor *= 0.00001;
		//outColor += vec4(ao,ao,ao,1);
	}
}
