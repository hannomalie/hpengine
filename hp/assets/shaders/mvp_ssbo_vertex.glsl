
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

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
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};

void main()
{
    int entityBufferIndex = entityBaseIndex + gl_InstanceID;
    Entity entity = entities[entityBufferIndex];
    //outEntityIndex = entityIndex;
    //outEntity = entity;
    //Material material = materials[int(entity.materialIndex)];
    //outMaterial = material;

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
