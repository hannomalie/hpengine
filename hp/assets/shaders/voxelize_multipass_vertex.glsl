
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform int entityIndex;

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

out vec4 color;
out vec2 texCoord;
out vec3 normalVec;
out vec3 normal_model;
out vec3 normal_world;
out vec3 normal_view;
out vec4 position_clip;
out vec4 position_world;

flat out Entity outEntity;
flat out int outEntityIndex;
flat out Material outMaterial;

void main(void) {

    Entity entity = entities[entityIndex];
    outEntityIndex = entityIndex;
    outEntity = entity;
    Material material = materials[int(entity.materialIndex)];
    outMaterial = material;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	mat4 mvp = (projectionMatrix * viewMatrix * modelMatrix);
	position_clip = mvp * positionModel;

	gl_Position = position_clip;

	color = in_Color;
	texCoord = in_TextureCoord;
	texCoord.y = 1 - in_TextureCoord.y;
	
	normalVec = in_Normal;
}