uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform int entityIndex;
uniform int materialIndex;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Tangent;
in vec3 in_Binormal;

out vec3 normal_world;
out vec4 position_world;

out vec3 v_vertex;
out vec3 v_normal;
out vec2 v_texcoord;

flat out Entity outEntity;
flat out int outEntityIndex;

uniform float sceneScale = 1f;
uniform float inverseSceneScale = 1f;
uniform int gridSize = 256;

void main(void) {
    Entity entity = entities[entityIndex];
    outEntityIndex = entityIndex;
    outEntity = entity;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	vec3 normal_model = in_Normal;

	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;

	v_vertex = position_world.xyz;
	v_normal = normal_world.xyz;
	v_texcoord = in_TextureCoord;

//    gl_Position = position_world;
}