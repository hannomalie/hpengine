uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 lastViewMatrix;
uniform mat4 viewProjectionMatrix;
uniform mat4 lightMatrix;

uniform int indirect = 1;
uniform int entityIndex = 0;
uniform vec3 eyePosition;
uniform int time = 0;


//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=4) buffer _entityOffsets {
	int entityOffsets[2000];
};


in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

flat out VertexShaderFlatOutput vertexShaderFlatOutput;
//out VertexShaderOutput vertexShaderOutput;

out vec4 color;
out vec2 texCoord;
out vec3 normalVec;
out vec3 normal_model;
out vec3 normal_world;
out vec3 normal_view;
out vec3 tangent_world;
out vec3 bitangent_world;
out vec4 position_clip;
out vec4 position_clip_last;
out vec4 position_clip_uv;
out vec4 position_world;
//out vec4 position_clip_shadow;
//out vec3 view_up;
//out vec3 view_back;
out vec3 lightVec;
out vec3 halfVec;
out vec3 eyeVec;
out vec3 eyePos_world;

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

//    entityBufferIndex = 0;

    Entity entity = entities[entityBufferIndex];

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	mat4 mvp = (viewProjectionMatrix * modelMatrix);

	position_clip_last = (projectionMatrix * lastViewMatrix * position_world);

	position_clip = mvp * positionModel;
	position_clip_uv.xyz = position_clip.xyz;
	position_clip_uv /= position_clip_uv.w;
	position_clip_uv.xyz += 1;
	position_clip_uv.xyz *= 0.5;
	
	color = in_Color;
	texCoord = in_TextureCoord;
	if(entity.invertTexcoordY == 1) {
	    texCoord.y = 1 - in_TextureCoord.y;
	} else {
	    texCoord.y = in_TextureCoord.y;
	}


	normalVec = in_Normal;
	normal_model = (vec4(in_Normal,0)).xyz;
	normal_world.x = dot(modelMatrix[0].xyz, normal_model);
    normal_world.y = dot(modelMatrix[1].xyz, normal_model);
    normal_world.z = dot(modelMatrix[2].xyz, normal_model);
    normal_world = normalize(normal_world);
	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;
	normal_view = (viewMatrix * vec4(normal_world,0)).xyz;

    vertexShaderFlatOutput.entity = entity;
    vertexShaderFlatOutput.entityBufferIndex = entityBufferIndex;
    vertexShaderFlatOutput.entityIndex = entityIndex;
    vertexShaderFlatOutput.material = material;
    vertexShaderFlatOutput.materialIndex = materialIndex;

    gl_Position = position_clip;

}
