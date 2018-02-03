#extension GL_ARB_shader_draw_parameters : require

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 lastViewMatrix;
uniform mat4 viewProjectionMatrix;
uniform mat4 lightMatrix;

uniform int indirect = 1;
uniform int entityIndex = 0;
uniform vec3 eyePosition;
uniform int time = 0;
//uniform vec3 lightPosition;


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
flat out mat3 TBN;
flat out Entity outEntity;
flat out int outEntityIndex;
flat out int outEntityBufferIndex;
flat out Material outMaterial;
flat out int outMaterialIndex;

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    //entityBufferIndex = 0;

    Entity entity = entities[entityBufferIndex];
    outEntity = entity;
    outEntityIndex = int(entity.entityIndex);

    outEntityBufferIndex = entityBufferIndex;

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];
    outMaterial = material;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	mat4 mvp = (viewProjectionMatrix * modelMatrix);
	position_clip = mvp * positionModel;

	position_clip_last = (projectionMatrix * lastViewMatrix * position_world);
	gl_Position = position_clip;
	//position_clip_shadow = projectionMatrixShadow * viewMatrixShadow * modelMatrix * vec4(in_Position.xyz,1);
	//position_clip_shadow.xyz /= position_clip_shadow.w;
	//position_clip_shadow.xyz += 1.0;
	//position_clip_shadow.xyz *= 0.5;
	
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

    #define use_precomputed_tangent_space_
    #ifdef use_precomputed_tangent_space
        vec3 tangent_model = in_Tangent;
        tangent_world.x = dot(modelMatrix[0].xyz, tangent_model);
        tangent_world.y = dot(modelMatrix[1].xyz, tangent_model);
        tangent_world.z = dot(modelMatrix[2].xyz, tangent_model);
        tangent_world = normalize(tangent_world);

        vec3 bitangent_model = in_Binormal;
        bitangent_world.x = dot(modelMatrix[0].xyz, bitangent_model);
        bitangent_world.y = dot(modelMatrix[1].xyz, bitangent_model);
        bitangent_world.z = dot(modelMatrix[2].xyz, bitangent_model);
        bitangent_world = normalize(bitangent_world);
        TBN = transpose(mat3(tangent_world, bitangent_world, normal_world));
    #endif
	eyePos_world = ( vec4(eyePosition,1)).xyz;
	eyeVec = (position_world.xyz - eyePos_world);

}
