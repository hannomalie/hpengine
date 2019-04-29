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
uniform float near = 0.1;
uniform float far = 100.0;

flat in VertexShaderFlatOutput vertexShaderFlatOutput;

layout(location=0)out vec4 out_positionRoughness; // position, roughness
layout(location=1)out vec4 out_normalAmbient; // normal, depth
layout(location=2)out vec4 out_colorMetallic; // color, metallic
layout(location=3)out vec4 out_motionDepthTransparency; // motion, probeIndices
layout(location=4)out vec4 out_depthAndIndices; // visibility


void main(void) {

    int entityIndex = vertexShaderFlatOutput.entityBufferIndex;
    Entity entity = vertexShaderFlatOutput.entity;
	Material material = vertexShaderFlatOutput.material;

	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	vec2 UV = texCoord;

	vec4 color = texture(diffuseMap, UV);
	if(color.a < 0.1) {
		discard;
	}
  	out_colorMetallic = color;

}
