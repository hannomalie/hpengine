#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=6) uniform samplerCube environmentMap;

uniform bool isSelected = false;

uniform int entityCount = 1;

uniform int materialIndex = -1;

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
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

uniform float sceneScale = 1f;
uniform float inverseSceneScale = 1f;
uniform int gridSize;

uniform bool useNormalMaps = true;

uniform vec3 directionalLightColor = vec3(1);

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
in vec4 pass_WorldPosition;

uniform vec3 eyeVec;
uniform vec3 eyePos_world;
in mat3 TBN;
flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;
flat in int outEntityBufferIndex;
flat in int outMaterialIndex;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility

//include(globals.glsl)

void main(void) {

	float depth = (position_clip.z / position_clip.w);

    out_position = pass_WorldPosition;
    out_position.w = 0;
    out_normal = vec4(normal_view, depth);
    out_normal.a = 1;
    vec3 sampleVector = -normalize(pass_WorldPosition.xyz-eyePos_world.xyz);
    out_color.rgb = textureLod(environmentMap, -sampleVector, 0).rgb;
    out_motion = vec4(0,0,depth,0);
    out_visibility = vec4(1, 1, materialIndex, 0);
//    gl_FragDepth = 1;
}
