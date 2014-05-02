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

uniform bool hasNormalMap;
uniform float normalMapWidth = 1;
uniform float normalMapHeight = 1;

uniform bool hasDiffuseMap;
uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

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

in vec3 eyeVec;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_albedo;

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

void main(void) {
	
	vec3 V = normalize(eyeVec);
	vec2 UV = texCoord;
	if (hasNormalMap) {
		UV.x = texCoord.x * normalMapWidth;
		UV.y = texCoord.y * normalMapHeight;
	}
	
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
	
	// NORMAL
	vec3 N = normal_world;
	vec3 PN = N;
	if (hasNormalMap) {
		PN = normalize((vec4(perturb_normal(PN, eyeVec, UV), 0)).xyz);
	}
	
	vec3 PN_view = (viewMatrix *vec4(PN, 0)).xyz;
	out_position = viewMatrix * position_world;
	out_normal = vec4(PN_view, position_clip.z / position_clip.w);
	
	vec4 color = color;
	if (hasDiffuseMap) {
		UV = texCoord;
		UV.x = texCoord.x * diffuseMapWidth;
		UV.y = texCoord.y * diffuseMapHeight;
		color = texture2D(diffuseMap, UV);
	}
	out_albedo = color;
}
