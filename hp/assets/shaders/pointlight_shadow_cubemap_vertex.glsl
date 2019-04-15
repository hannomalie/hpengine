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

uniform int indirect = 1;
uniform int entityIndex = 0;

uniform vec3 pointLightPositionWorld;
uniform float pointLightRadius;

uniform bool isBack;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Tangent;
in vec3 in_Binormal;

out vec4 vs_pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out vec2 vs_pass_texCoord;
out float clip;

void main()
{
    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    Entity entity = entities[entityBufferIndex];
    mat4 modelMatrix = mat4(entity.modelMatrix);

	vs_pass_WorldPosition = modelMatrix * vec4(in_Position.xyz,1);

    //gl_Position = pass_WorldPosition;

	vs_pass_texCoord = in_TextureCoord;
	vs_pass_texCoord.y = 1 - in_TextureCoord.y;
}