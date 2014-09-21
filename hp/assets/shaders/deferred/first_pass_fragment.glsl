#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;

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
uniform float materialGlossiness = 0;
//uniform vec3 materialAmbientColor = vec3(0,0,0);
//uniform float materialTransparency = 1;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int environmentProbeIndex = 0;
uniform vec3 environmentMapWorldPosition = vec3(0,0,0);
uniform vec3 environmentMapMin = vec3(-1,-1,-1);
uniform vec3 environmentMapMax = vec3(1,1,1);

uniform float time = 0;

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
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_color;
layout(location=3)out vec4 out_specular;
layout(location=4)out vec4 out_probe;

float linearizeDepth(float z)
{
  float n = near; // camera z near
  float f = far; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
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
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, -V, texcoord );
	return normalize( TBN * map );
}

#define kPI 3.1415926536f
vec2 encodeNormal(vec3 n) {
    return vec2((vec2(atan(n.y,n.x)/kPI, n.z)+1.0)*0.5);
}

vec3 boxProjection(vec3 texCoords3d) {
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
	
	//texCoords3d = normalize(posonbox - vec3(0,0,0));
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}

void main(void) {
	
	vec3 V = -normalize((position_world.xyz - eyePos_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
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
		float height = (texture2D(normalMap, UV).rgb).y;//texture2D(heightMap, UV).r;
		height = height * 2 - 1;
		float v = height * 0.014;
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
	
	// NORMAL
	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
#ifdef use_normalMap
	PN_world = normalize(perturb_normal(normal_world, V, UV));
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	out_position = viewMatrix * position_world;
	out_position.w = materialGlossiness;
	float depth = (position_clip.z / position_clip.w);
	
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);
	//out_normal.z = environmentProbeIndex;
	
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
	out_color.w = reflectiveness;

#ifdef use_reflectionMap
	out_color.w = length(texture2D(reflectionMap, UV));
#endif
//vec3 texCoords3d = eyeVec;
//vec3 texCoords3d = PN_world;
vec3 texCoords3d = normalize(reflect(V, PN_world));

///////////////////////////////////////////////////////////////////////
/*vec3 nrdir = normalize(texCoords3d);
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

//texCoords3d = normalize(posonbox - vec3(0,0,0));
texCoords3d = normalize(posonbox - environmentMapWorldPosition.xyz);*/
texCoords3d = boxProjection(texCoords3d);
///////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////
// http://wiki.cgsociety.org/index.php/Ray_Sphere_Intersection
/*float a = dot(nrdir,nrdir);
float b = 2.0 * dot(nrdir, position_world.xyz - environmentMapWorldPosition);
float c = dot(position_world.xyz, environmentMapWorldPosition) - environmentMapSize*environmentMapSize;
float discrim = b * b - 4.0 * a * c;
float q;
vec4 reflColor = vec4(1, 0, 0, 0);
if (discrim > 0) {
  q = ((abs(sqrt(discrim) - b) / 2.0));
  float t0 = q / a;
  float t1 = c / q;
    if (t0 > t1)
	{
	    float temp = t0;
	    t0 = t1;
	    t1 = temp;
	}
    if (t0 < 0)
    {
        texCoords3d = t1 * nrdir - position_world.xyz;
        texCoords3d.y = -texCoords3d.y;
    }
}*/
///////////////////////////////////////////////////////////////////////

out_color.rgb = mix(out_color.rgb, texture(environmentMap, texCoords3d).rgb, 0);//reflectiveness);
out_probe.rgba = texture(environmentMap, texCoords3d).rgba;
//out_probe.rgba = textureLod(environmentMap, texCoords3d, 8).rgba;

if (useParallax) {
	texCoords3d -= texCoords3d * 0.0000001 * 0.0001 * texture(environmentMap, texCoords3d).a;
	texCoords3d = boxProjection(texCoords3d);
	out_probe.rgba = texture(environmentMap, texCoords3d).rgba;
}

vec4 specularColor = vec4(materialSpecularColor, materialSpecularCoefficient);
#ifdef use_specularMap
		UV.x = texCoord.x * specularMapWidth;
		UV.y = texCoord.y * specularMapHeight;
		UV = texCoord + uvParallax;
		vec3 specularSample = texture2D(specularMap, UV).xyz;
		specularColor = vec4(specularSample, materialSpecularCoefficient);
#endif
	out_specular = specularColor;
	
}
