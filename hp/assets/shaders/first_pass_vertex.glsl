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

#ifdef ANIMATED
layout(std430, binding=6) buffer _joints {
	mat4 joints[2000];
};
#endif

//////
struct VertexPacked {
	vec4 position;
	vec4 texCoord;
	vec4 normal;
};
layout(std430, binding=7) buffer _vertices {
	int VertexPacked[];
};
//////

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
#ifdef ANIMATED
in vec4 in_Weights;
in ivec4 in_JointIndices;
#endif

flat out VertexShaderFlatOutput vertexShaderFlatOutput;
out VertexShaderOutput vertexShaderOutput;

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    Entity entity = entities[entityBufferIndex];

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];

    mat4 modelMatrix = entity.modelMatrix;

	vec4 positionModel = vec4(in_Position.xyz,1);

#ifdef ANIMATED

	vec4 initPos = vec4(0, 0, 0, 0);
	int count = 0;
	const int MAX_WEIGHTS = 4;
	int frameIndex = entity.animationFrame0;
	int currentJoint = 150 * frameIndex; // MAX_JOINTS per animation frame is 150
	for(int i = 0; i < MAX_WEIGHTS; i++)
	{
		float weight = in_Weights[i];
		if(weight > 0) {
			count++;
			int jointIndex = entity.baseJointIndex + currentJoint + in_JointIndices[i];
			vec4 tmpPos = joints[jointIndex] * vec4(positionModel.xyz, 1.0);
			initPos += weight * tmpPos;
		}
	}
	if (count == 0)
	{
		initPos = vec4(positionModel.xyz, 1.0);
	}

	positionModel = initPos;
#endif

	vec4 position_world = modelMatrix * positionModel;

	mat4 mvp = (viewProjectionMatrix * modelMatrix);

	vec4 position_clip_last = (projectionMatrix * lastViewMatrix * position_world);

	vec4 position_clip = mvp * positionModel;
	vec4 position_clip_uv;
	position_clip_uv.xyz = position_clip.xyz;
	position_clip_uv /= position_clip_uv.w;
	position_clip_uv.xyz += 1;
	position_clip_uv.xyz *= 0.5;

	vec4 color = in_Color;
	vec2 texCoord = in_TextureCoord;
	if(entity.invertTexcoordY == 1) {
	    texCoord.y = 1 - in_TextureCoord.y;
	} else {
	    texCoord.y = in_TextureCoord.y;
	}


	vec3 normalVec = in_Normal;
	vec3 normal_model = (vec4(in_Normal,0)).xyz;
	vec3 normal_world;
	normal_world.x = dot(modelMatrix[0].xyz, normal_model);
    normal_world.y = dot(modelMatrix[1].xyz, normal_model);
    normal_world.z = dot(modelMatrix[2].xyz, normal_model);
    normal_world = normalize(normal_world);
	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;
	vec3 normal_view = (viewMatrix * vec4(normal_world,0)).xyz;




	vertexShaderOutput.color = color;
	vertexShaderOutput.texCoord = texCoord;
	vertexShaderOutput.normalVec = normalVec;
	vertexShaderOutput.normal_model = normal_model;
	vertexShaderOutput.normal_world = normal_world;
	vertexShaderOutput.normal_view = normal_view;
	vertexShaderOutput.position_clip = position_clip;
	vertexShaderOutput.position_clip_last = position_clip_last;
	vertexShaderOutput.position_clip_uv = position_clip_uv;
	vertexShaderOutput.position_world = position_world;


    vertexShaderFlatOutput.entity = entity;
    vertexShaderFlatOutput.entityBufferIndex = entityBufferIndex;
    vertexShaderFlatOutput.entityIndex = entityIndex;
    vertexShaderFlatOutput.material = material;
    vertexShaderFlatOutput.materialIndex = materialIndex;

    gl_Position = position_clip;

}
