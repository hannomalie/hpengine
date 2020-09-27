
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;

layout(binding=8) uniform samplerCubeArray probes;

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

uniform vec3 probeCenter = vec3(0,0,0);
uniform int probeIndex = 0;

uniform bool showContent = false;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

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
layout(location=4)out vec4 out_visibility; // visibility

#define kPI 3.1415926536f
vec2 encodeNormal(vec3 n) {
    return vec2((vec2(atan(n.y,n.x)/kPI, n.z)+1.0)*0.5);
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

vec3 boxProjection(vec3 position_world, vec3 texCoords3d, vec3 environmentMapMin, vec3 environmentMapMax) {
	vec3 posonbox = getIntersectionPoint(position_world, texCoords3d, environmentMapMin, environmentMapMax);
	
	//texCoords3d = normalize(posonbox - vec3(0,0,0));
	vec3 environmentMapWorldPosition = (environmentMapMax + environmentMapMin)/2;
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}

void main(void) {
	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 blurVec = position_clip_post_w.xy - position_clip_last_post_w.xy;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

	vec2 uvParallax = vec2(0,0);
	// NORMAL
	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
#ifdef use_normalMap
	PN_world = normalize(perturb_normal(PN_world, V, UV));
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	out_position = viewMatrix * position_world;
	
	float depth = (position_clip.z / position_clip.w);
	
	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);
	
	vec3 probesSampleDirection = position_world.xyz - probeCenter;
	//probesSampleDirection = boxProjection(position_world.xyz, probesSampleDirection, environmentMapMin[probeIndex], environmentMapMax[probeIndex]);
	vec4 probeTextureSample = textureLod(probes, vec4(normalize(probesSampleDirection), probeIndex), 0);
	
	vec4 color = vec4(materialDiffuseColor, 1);
	if(showContent) {
		if(probeTextureSample.a < 0.1) { discard; } 
		color.rgb = probeTextureSample.rgb;
	}
	
  	out_color = color;
  	out_color.w = materialMetallic;

	out_position.w = materialRoughness;

  	out_motion = vec4(blurVec,probeIndex1,probeIndex2);
  	out_visibility = vec4(1,depth,depth,0);
}
