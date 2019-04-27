layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=6) uniform samplerCube environmentMap;

uniform bool isSelected = false;

uniform int entityCount = 1;

uniform bool useParallax;
uniform bool useSteepParallax;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;

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
flat in mat3 TBN;
flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;
flat in int outEntityBufferIndex;
flat in int outMaterialIndex;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_positionRoughness; // position, roughness
layout(location=1)out vec4 out_normalAmbient; // normal, depth
layout(location=2)out vec4 out_colorMetallic; // color, metallic
layout(location=3)out vec4 out_motionDepthTransparency; // motion, probeIndices
layout(location=4)out vec4 out_depthAndIndices; // visibility

//xxxinclude(globals.glsl)

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
vec2 parallaxMapping(sampler2D heightMap, vec2 texCoords, vec3 viewDir, float height_scale, float parallaxBias)
{
    float height =  texture(heightMap, texCoords).r;
    if(height < parallaxBias) {
        height = 0;
    };
    vec2 p = viewDir.xy / viewDir.z * (height * height_scale);
    return texCoords - p;
}
void main(void) {

    int entityIndex = outEntityBufferIndex;
    Entity entity = outEntity;
	Material material = outMaterial;

    vec3 materialDiffuseColor = vec3(material.diffuseR,
                                     material.diffuseG,
                                     material.diffuseB);

	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	vec2 UV = texCoord;

	vec4 color = texture(diffuseMap, UV);
	if(color.a < 0.1) {
		discard;
	}
  	out_colorMetallic = color;

}
