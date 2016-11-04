#extension GL_ARB_shader_draw_parameters : require

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform int entityIndex = 0;
//uniform vec3 lightPosition;


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

out vec3 v_lightmapTextureCoord;
out vec4 position_clip;

void main(void) {

//TODO: Fix this for direct drawing
    int realEntityIndex = gl_DrawIDARB + entityIndex;
    int outEntityIndex = realEntityIndex;

    int offset = entityOffsets[realEntityIndex];
    int outEntityBufferIndex = offset + gl_InstanceID;

    Entity entity = entities[outEntityBufferIndex];
    Entity outEntity = entity;

    mat4 modelMatrix = mat4(entity.modelMatrix);
	vec4 positionModel = vec4(in_Position.xyz,1);
	mat4 mvp = (projectionMatrix * viewMatrix * modelMatrix);
	vec4 position_clip = mvp * positionModel;
	gl_Position = position_clip;

	v_lightmapTextureCoord = in_LightmapTextureCoord;

}
