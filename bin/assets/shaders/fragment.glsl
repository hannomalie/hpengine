#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D shadowMap;

uniform bool useParallax;

uniform bool hasDiffuseMap;
uniform bool hasNormalMap;
uniform bool hasSpecularMap;

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec4 position_clip;
in vec4 position_clip_shadow;
in vec4 position_world;
in vec3 view_up;
in vec3 view_back;

in vec3 lightVec;
in vec3 halfVec;
in vec3 eyeVec;

out vec4 outColor;

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
void main(void) {

	if (useParallax) {
		float height = texture2D(heightMap, texCoord).r;
		float v = height * 0.106 - 0.012;
		vec2 newCoords = texCoord + (eyeVec.xy * v);
		outColor = vec4(texture2D(diffuseMap, newCoords).rgb, 1);

	} else {
	
		vec4 diffuseMaterial = vec4(0.5,0.5,0.5,1);
		if (hasDiffuseMap) {
			diffuseMaterial = texture2D(diffuseMap, texCoord);
		}
		
		vec4 diffuseLight = vec4(1,1,1,1);
		vec4 ambientLight = vec4(0.2, 0.2, 0.2, 0.2);
		
		vec3 normal = normalize(normal_model);
		if (hasNormalMap) {
			normal = perturb_normal( normalize(normalVec), eyeVec, texCoord );
		}
		
		float specularStrength = 0;
		if (hasSpecularMap) {
			specularStrength = texture2D(specularMap, texCoord).r;
		}
		
		float NdotL = clamp(dot(normal,normalize(lightVec)),0.0,1.0);
		vec3 reflection = normalize( ( ( 2.0 * normalize(normal) ) * NdotL ) - normalize(lightVec) );
		float RdotV = max( 0.0, dot(reflection, normalize(eyeVec)));
		float specular = pow(RdotV, specularStrength) * specularStrength * NdotL;
		
		float visibility = 1;
		if (true) {
			visibility = eval_shadow_poisson(position_clip_shadow.xy);
		}
		
		outColor = diffuseMaterial * NdotL * diffuseLight + diffuseLight * specular;
		outColor *= visibility;
		outColor += diffuseMaterial*ambientLight;
		
		//outColor *= 0.01;
		//outColor += vec4(normal,1);
	}
}
