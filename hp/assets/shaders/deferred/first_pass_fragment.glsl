#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;
layout(binding=7) uniform sampler2D roughnessMap;

uniform bool useParallax;
uniform bool useSteepParallax;

uniform float normalMapWidth = 1;
uniform float normalMapHeight = 1;

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

uniform float specularMapWidth = 1;
uniform float specularMapHeight = 1;

uniform vec3 materialDiffuseColor = vec3(0,0,0);
uniform vec3 materialSpecularColor = vec3(0,0,0);
uniform float materialSpecularCoefficient = 0;
uniform float materialRoughness = 0;
uniform float materialMetallic = 0;
uniform int probeIndex1 = 0;
uniform int probeIndex2 = 0;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform float time = 0;

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
//in mat3 TBN;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices

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
	vec3 map = (textureLod(normalMap, texcoord, 0)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}

#define kPI 3.1415926536f
vec2 encodeNormal(vec3 n) {
    return vec2((vec2(atan(n.y,n.x)/kPI, n.z)+1.0)*0.5);
}

void main(void) {
	
	vec3 V = -normalize((position_world.xyz - eyePos_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 blurVec = position_clip_post_w.xy - position_clip_last_post_w.xy;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

#ifdef use_normalMap
		UV.x = UV.x * normalMapWidth;
		UV.y = UV.y * normalMapHeight;
		//UV = UV + time/2000.0;
#endif

	vec2 uvParallax = vec2(0,0);

	if (useParallax) {
		float height = (textureLod(normalMap, UV,0).rgb).y;//texture2D(heightMap, UV).r;
		height = height * 2 - 1;
		float v = height * 0.4;
		uvParallax = (V.xy * v);
		UV = UV + uvParallax;
	} else if (useSteepParallax) {
		float n = 20;
		float bumpScale = 0.02;
		float step = 1/n;
		vec2 dt = V.xy * bumpScale / (n * V.z);
		
		float height = 1;
		vec2 t = UV;
		vec4 nb = texture2D(normalMap, t);
		nb = nb * 2 - 1;
		while (length(nb.xyz) < height) { 
			height -= step;
			t += dt; 
			nb = texture2D(normalMap, t); 
			nb = nb * 2 - 1;
		}
		UV = t;
	}
	
    mat3 TBN = transpose(mat3(
        (vec4(normalize(tangent_world),0)).xyz,
        (vec4(normalize(bitangent_world),0)).xyz,
        normalize(normal_world)
    ));
	
	// NORMAL
	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
#ifdef use_normalMap
	PN_world = normalize(perturb_normal(PN_world, V, UV));
	//PN_world = inverse(TBN) * normalize((texture(normalMap, UV)*2-1).xyz);
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	out_position = viewMatrix * position_world;
	
	float depth = (position_clip.z / position_clip.w);
	
	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);
	
	vec4 color = vec4(materialDiffuseColor, 1);
#ifdef use_diffuseMap
	UV = texCoord;
	UV.x = texCoord.x * diffuseMapWidth;
	UV.y = texCoord.y * diffuseMapHeight;
	UV += uvParallax;
	color = texture2D(diffuseMap, UV);
	if(color.a<0.1)
	{
		discard;
	}
#endif
  	out_color = color;
  	out_color.w = materialMetallic;

	out_position.w = materialRoughness;
#ifdef use_roughnessMap
	UV.x = texCoord.x * roughnessMapWidth;
	UV.y = texCoord.y * roughnessMapHeight;
	UV = texCoord + uvParallax;
	float r = texture2D(roughnessMap, UV).x;
	out_position.w *= r;
#endif

	
#ifdef use_specularMap
	UV.x = texCoord.x * specularMapWidth;
	UV.y = texCoord.y * specularMapHeight;
	UV = texCoord + uvParallax;
	vec3 specularSample = texture2D(specularMap, UV).xyz;
	float glossiness = length(specularSample)/length(vec3(1,1,1));
	const float glossinessBias = 1.5;
	out_position.w = clamp(glossinessBias-glossiness, 0, 1) * (materialRoughness);
#endif

  	out_motion = vec4(blurVec,probeIndex1,probeIndex2);
}
