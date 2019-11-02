in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

uniform int indirect = 1;
uniform int entityIndex;
uniform int entityBaseIndex;

//in vec4 in_position;
//in vec2 in_TextureCoord;
//in vec3 in_Normal;

out vec4 pass_Position;
out vec4 pass_WorldPosition;
out vec3 normal_world;
out vec2 texCoord;


//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _directionalLight {
	DirectionalLightState directionalLight;
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=4) buffer _entityOffsets {
	int entityOffsets[2000];
};

void main()
{
	int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;

	if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }
	Entity entity = entities[entityBufferIndex];


    mat4 projectionMatrix = directionalLight.projectionMatrix;
    mat4 viewMatrix = directionalLight.viewMatrix;

    mat4 modelMatrix = mat4(entity.modelMatrix);

	pass_WorldPosition = modelMatrix * vec4(in_Position.xyz,1);
	pass_Position = projectionMatrix * viewMatrix * pass_WorldPosition;
    gl_Position = pass_Position;
	normal_world.x = dot(modelMatrix[0].xyz, in_Normal);
    normal_world.y = dot(modelMatrix[1].xyz, in_Normal);
    normal_world.z = dot(modelMatrix[2].xyz, in_Normal);
	normal_world = (inverse(transpose(modelMatrix)) * vec4(in_Normal,0)).xyz;
	//normal_world = modelMatrix * vec4(in_Normal,0)).xyz;
	
	texCoord = in_TextureCoord;
	texCoord.y = 1 - in_TextureCoord.y;
}
