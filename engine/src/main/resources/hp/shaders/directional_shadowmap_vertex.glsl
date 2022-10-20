in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
#ifdef ANIMATED
in vec4 in_Weights;
in ivec4 in_JointIndices;
#endif

uniform int indirect = 1;
uniform int entityIndex;
uniform int entityBaseIndex;

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

#ifdef ANIMATED
layout(std430, binding=6) buffer _joints {
	mat4 joints[2000];
};

layout(std430, binding=7) buffer _vertices {
	VertexAnimatedPacked vertices[];
};
#else
layout(std430, binding=7) buffer _vertices {
	VertexPacked vertices[];
};
#endif

void main()
{
	int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;

	if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }
	Entity entity = entities[entityBufferIndex];


    mat4 projectionMatrix = directionalLight.projectionMatrix;
    mat4 viewMatrix = directionalLight.viewMatrix;

    mat4 modelMatrix = mat4(entity.modelMatrix);

#ifdef ANIMATED
	VertexAnimatedPacked vertex;
#else
	VertexPacked vertex;
#endif

	int vertexIndex = gl_VertexID;
	vertex = vertices[vertexIndex];
	vec4 positionModel = vertex.position;

#ifdef ANIMATED
	vec4 weightsIn;
	ivec4 jointIndices;
	weightsIn = vertex.weights;
	jointIndices = vertex.jointIndices;

	vec4 initPos = vec4(0, 0, 0, 0);
	int count = 0;
	const int MAX_WEIGHTS = 4;
	int frameIndex = entity.animationFrame0;
	int currentJoint = 150 * frameIndex; // MAX_JOINTS per animation frame is 150
	for(int i = 0; i < MAX_WEIGHTS; i++)
	{
		float weight = weightsIn[i];
		if(weight > 0) {
			count++;
			int jointIndex = entity.baseJointIndex + currentJoint + jointIndices[i];
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

	vec3 normal = vertex.normal.xyz;
	vec2 texCoord = vertex.texCoord.xy;

	pass_WorldPosition = modelMatrix * vec4(positionModel.xyz,1);
	pass_Position = projectionMatrix * viewMatrix * pass_WorldPosition;
    gl_Position = pass_Position;

	normal_world.x = dot(modelMatrix[0].xyz, normal);
    normal_world.y = dot(modelMatrix[1].xyz, normal);
    normal_world.z = dot(modelMatrix[2].xyz, normal);
	normal_world = normalize(normal_world);
	normal_world = (inverse(transpose(modelMatrix)) * vec4(normal,0)).xyz;
	//normal_world = modelMatrix * vec4(in_Normal,0)).xyz;
	
	texCoord = texCoord;
	if(entity.invertTexcoordY == 1) {
		texCoord.y = 1 - texCoord.y;
	}
}
