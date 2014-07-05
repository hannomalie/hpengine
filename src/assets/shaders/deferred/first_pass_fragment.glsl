#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;

layout(binding=6) uniform samplerCube cubeMap;
//layout(binding=5) uniform sampler2D shadowMap;
//layout(binding=6) uniform sampler2D depthMap;

uniform bool useParallax;
uniform bool useSteepParallax;
uniform float reflectiveness;

uniform float normalMapWidth = 1;

uniform float normalMapHeight = 1;

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

uniform float specularMapWidth = 1;
uniform float specularMapHeight = 1;


uniform vec3 materialDiffuseColor = vec3(0,0,0);
uniform vec3 materialSpecularColor = vec3(0,0,0);
uniform float materialSpecularCoefficient = 0;
//uniform vec3 materialAmbientColor = vec3(0,0,0);
//uniform float materialTransparency = 1;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec4 position_clip;
//in vec4 position_clip_uv;
//in vec4 position_clip_shadow;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_color;
layout(location=3)out vec4 out_specular;

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
	
	vec3 V = normalize((position_world.xyz - eyePos_world.xyz).xyz);
	//V = (viewMatrix * vec4(V, 0)).xyz;
	vec2 UV = texCoord;

#ifdef use_normalMap
		UV.x = texCoord.x * normalMapWidth;
		UV.y = texCoord.y * normalMapHeight;
#endif
	
	if (useParallax) {
		float height = length(texture2D(normalMap, UV).rgb);//texture2D(heightMap, UV).r;
		float v = height * 0.02106 - 0.012;
		UV = UV + (V.xy * v);
	} else if (useSteepParallax) {
		float n = 30;
		float bumpScale = 100;
		float step = 1/n;
		vec2 dt = V.xy * bumpScale / (n * V.z);
		
		float height = 1;
		vec2 t = UV;
		vec4 nb = texture2D(normalMap, t);
		while (length(nb.xyz) < height) { 
			height -= step;
			t += dt; 
			nb = texture2D(normalMap, t); 
		}
		UV = t;
	}
	
	// NORMAL
	vec3 PN_view =  (viewMatrix * vec4(normal_model,0)).xyz;
#ifdef use_normalMap
		PN_view = ((viewMatrix * vec4(perturb_normal(normal_world, V, UV), 0)).xyz);
#endif
	
	out_position = viewMatrix * position_world;
	float depth = position_clip.z / position_clip.w;
	
	out_normal = vec4(PN_view, depth);
	
	vec4 color = vec4(materialDiffuseColor, 1);
#ifdef use_diffuseMap
	UV = texCoord;
	UV.x = texCoord.x * diffuseMapWidth;
	UV.y = texCoord.y * diffuseMapHeight;
	color = texture2D(diffuseMap, UV);
	if(color.a<0.1)
	{
		discard;
	}
#endif
	out_color = color;
	out_color.w = reflectiveness;

#ifdef use_reflectionMap
	float reflect_factor = texture2D(reflectionMap, UV).x;
	vec3 texCoords3d = normalize(reflect(V, normal_world));
	//texCoords3d.y *= -1;
	out_color = mix(texture(cubeMap, texCoords3d), out_color, reflect_factor);
	out_color.w = reflect_factor;
#endif

	vec4 specularColor = vec4(materialSpecularColor, materialSpecularCoefficient);
#ifdef use_specularMap
		UV = texCoord;
		UV.x = texCoord.x * specularMapWidth;
		UV.y = texCoord.y * specularMapHeight;
		vec3 specularSample = texture2D(specularMap, UV).xyz;
		specularColor = vec4(specularSample, materialSpecularCoefficient);
#endif
	out_specular = specularColor;
	
}
