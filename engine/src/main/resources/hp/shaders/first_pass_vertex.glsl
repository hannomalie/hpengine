uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 lastViewMatrix;
uniform mat4 viewProjectionMatrix;
uniform mat4 lightMatrix;

uniform int indirect = 1;
uniform int entityIndex = 0;
uniform vec3 eyePosition;
uniform int time = 0;

#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D occlusionMap;
uniform bool hasOcclusionMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif

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

layout(std430, binding=7) buffer _vertices {
	VertexAnimatedPacked vertices[];
};
#else
layout(std430, binding=7) buffer _vertices {
	VertexPacked vertices[];
};
#endif

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

//include(globals.glsl)

void main(void) {

    int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
    if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

    Entity entity = entities[entityBufferIndex];

    int materialIndex = int(entity.materialIndex);
    Material material = materials[materialIndex];

    mat4 modelMatrix = entity.modelMatrix;

#ifdef BINDLESSTEXTURES
	sampler2D diffuseMap;
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) { diffuseMap = sampler2D(material.handleDiffuse); }

	sampler2D normalMap;
	bool hasNormalMap = uint64_t(material.handleNormal) > 0;
	if(hasNormalMap) { normalMap = sampler2D(material.handleNormal); }

	sampler2D specularMap;
	bool hasSpecularMap = uint64_t(material.handleSpecular) > 0;
	if(hasSpecularMap) { specularMap = sampler2D(material.handleSpecular); }

	sampler2D heightMap;
	bool hasHeightMap = uint64_t(material.handleHeight) > 0;
	if(hasHeightMap) { heightMap = sampler2D(material.handleHeight); };

	sampler2D occlusionMap;
	bool hasOcclusionMap = uint64_t(material.handleOcclusion) > 0;
	if(hasOcclusionMap) { occlusionMap = sampler2D(material.handleOcclusion); }

	sampler2D roughnessMap;
	bool hasRoughnessMap = uint64_t(material.handleRoughness) != 0;
	if(hasRoughnessMap) { roughnessMap = sampler2D(material.handleRoughness); }
#endif

#ifdef ANIMATED
	VertexAnimatedPacked vertex;
#else
	VertexPacked vertex;
#endif

	const bool programmableVertexPulling = true;
	if(programmableVertexPulling) {
		int vertexIndex = gl_VertexID;
		vertex = vertices[vertexIndex];
		vertex.position.w = 1;
	} else {
		vertex.position = vec4(in_Position.xyz,1);
		vertex.texCoord = vec4(in_TextureCoord, 0, 0);
		vertex.normal = vec4(in_Normal, 0);
#ifdef ANIMATED
		vertex.jointIndices = in_JointIndices;
		vertex.weights = in_Weights;
#endif

	}

	if(entity.invertTexcoordY == 1) {
		vertex.texCoord.y = 1 - vertex.texCoord.y;
	}

	vec4 positionModel = vertex.position;
#ifdef ANIMATED
	vec4 weightsIn;
	ivec4 jointIndices;
	if(programmableVertexPulling) {
		weightsIn = vertex.weights;
		jointIndices = vertex.jointIndices;
	} else {
		weightsIn = in_Weights;
		jointIndices = in_JointIndices;
	}

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

	vec4 color = in_Color;
	vec2 texCoord = vertex.texCoord.xy;

	vec4 position_world = modelMatrix * positionModel;
	//AFTER_POSITION

	mat4 mvp = (viewProjectionMatrix * modelMatrix);

	vec4 position_clip_last = (projectionMatrix * lastViewMatrix * position_world);

	vec4 position_clip = mvp * positionModel;
	vec4 position_clip_uv;
	position_clip_uv.xyz = position_clip.xyz;
	position_clip_uv /= position_clip_uv.w;
	position_clip_uv.xyz += 1;
	position_clip_uv.xyz *= 0.5;



	vec3 normalVec = vertex.normal.xyz;
	vec3 normal_model = vertex.normal.xyz;
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
	//END
}
