#extension GL_ARB_shader_draw_parameters : require

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 lastViewMatrix;
//uniform mat4 modelMatrix;
uniform mat4 lightMatrix;

uniform int entityIndex = 0;
uniform vec3 eyePosition;
uniform int time = 0;
//uniform vec3 lightPosition;
uniform int entityCount = 1;

uniform float lightmapWidth;
uniform float lightmapHeight;


//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=4) buffer _entityOffsets {
	int entityOffsets[1000];
};


in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_LightmapTextureCoord;
in vec3 in_Tangent;
in vec3 in_Binormal;

out vec4 color;
out vec2 texCoord;
out vec2 lightmapTexCoord;
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

//include(globals.glsl)

void main(void) {

//TODO: Fix this for direct drawing
    int realEntityIndex = gl_DrawIDARB + entityIndex;
    outEntityIndex = realEntityIndex;

    int offset = entityOffsets[realEntityIndex];
    outEntityBufferIndex = offset + gl_InstanceID;

    Entity entity = entities[outEntityBufferIndex];
    outEntity = entity;
    Material material = materials[int(entity.materialIndex)];
    outMaterial = material;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	vec4 positionModel = vec4(in_Position.xyz,1);
	position_world = modelMatrix * positionModel;

	mat4 mvp = (projectionMatrix * viewMatrix * modelMatrix);

    float inverseEntityCount = 1f/float(entityCount);
    vec2 scaledLightmapCoords = scaleLightmapCoords(in_LightmapTextureCoord, lightmapWidth, lightmapHeight);

    lightmapTexCoord = scaledLightmapCoords.xy;
    vec2 screenCoords = scaledLightmapCoords.xy;
    screenCoords *= 2;
    screenCoords -= 1;
	gl_Position = vec4(screenCoords, 0, 1);

	color = in_Color;
	texCoord = in_TextureCoord;
	texCoord.y = 1 - in_TextureCoord.y;


	normalVec = in_Normal;
	normal_model = (vec4(in_Normal,0)).xyz;
	normal_world.x = dot(modelMatrix[0].xyz, normal_model);
    normal_world.y = dot(modelMatrix[1].xyz, normal_model);
    normal_world.z = dot(modelMatrix[2].xyz, normal_model);
    normal_world = normalize(normal_world);
	normal_view = (viewMatrix * vec4(normal_world,0)).xyz;

	/*
	eyePos_world = ( vec4(eyePosition,1)).xyz;
	eyeVec = (position_world.xyz - eyePos_world);
    */
}