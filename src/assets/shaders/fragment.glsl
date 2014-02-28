#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D shadowMap;

uniform bool useParallax;

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 pass_Color;
in vec2 pass_TextureCoord;
in vec3 pass_Normal;
in vec4 pass_Position;
in vec4 pass_PositionShadow;
in vec4 pass_PositionWorld;
in vec3 pass_Up;
in vec3 pass_Back;

in vec3 pass_LightDirection;
in vec3 pass_LightVec;
in vec3 pass_HalfVec;
in vec3 pass_eyeVec;

out vec4 out_Color;

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
	vec3 map = texture2D( normalMap, texcoord ).xyz;
	mat3 TBN = cotangent_frame( N, -V, texcoord );
	return normalize( TBN * map );
}
const float epsilon = 0.0025;
float eval_shadow (vec2 texcoods) {
	float shadow = texture (shadowMap, texcoods).r;
	if (shadow + epsilon < pass_PositionShadow.z) {
		return 0.2; // shadowed
	}
	return 1.0; // not shadowed
}

float eval_shadow_poisson (vec2 texcoods) {
	float shadow = 1.0;
	for (int i=0;i<4;i++){
		float mapSample = texture(shadowMap, texcoods + poissonDisk[i]/700).r;
		if (mapSample + epsilon < pass_PositionShadow.z) {
			shadow -= 0.2;
		}
	}
	return shadow; // not shadowed
}
void main(void) {

	if (useParallax) {
		float height = texture2D(heightMap, pass_TextureCoord).r;
		float v = height * 0.106 - 0.012;
		vec2 newCoords = pass_TextureCoord + (pass_eyeVec.xy * v);
		out_Color = vec4(texture2D(diffuseMap, newCoords).rgb, 1);

	} else {
		vec4 diffuseMaterial = texture2D(diffuseMap, pass_TextureCoord);
		vec4 specularMaterial = texture2D(specularMap, pass_TextureCoord);
		vec4 occlusionMaterial = texture2D(occlusionMap, pass_TextureCoord);
		vec4 diffuseLight = vec4(1,1,1,1);
		vec4 specularLight = vec4(1, 1, 1, 1);
		vec4 ambientLight = vec4(0.2, 0.2, 0.2, 0.2);
	
		vec3 normal = 2*texture2D(normalMap, pass_TextureCoord).rgb - 1.0;
		if (true) {
			normal = perturb_normal( normalize(pass_Normal), pass_eyeVec, pass_TextureCoord );
		}
		//normal.y = -normal.y;
		normal = normalize (normal);
		
		float shininess;
		
		float lamberFactor = max(dot(pass_LightVec, normal), 0.0);
		if (lamberFactor > 0.0)
		{
			shininess = 0.3*pow(max(dot(pass_HalfVec, normal), 0.0), 2.0);
			out_Color = diffuseMaterial * diffuseLight * lamberFactor;
			out_Color += specularMaterial * specularLight * shininess;
		}
		
		float visibility = eval_shadow_poisson(pass_PositionShadow.xy);
		
		out_Color += diffuseMaterial*ambientLight;
		
		out_Color *= visibility;
		//out_Color *= 0.000000001;
		//out_Color += vec4(visibility,visibility,visibility,1);
		
		
	}
}
