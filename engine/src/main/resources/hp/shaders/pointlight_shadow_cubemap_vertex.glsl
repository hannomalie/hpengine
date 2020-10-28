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

uniform int indirect = 0;
uniform int entityIndex = 0;

uniform vec3 pointLightPositionWorld;
uniform float pointLightRadius;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
#ifdef ANIMATED
in vec4 in_Weights;
in ivec4 in_JointIndices;
#endif

out vec4 vs_pass_WorldPosition;
out vec2 vs_pass_texCoord;
flat out Entity vs_pass_Entity;
flat out Material vs_pass_Material;

void main()
{

	int entityBufferIndex = entityOffsets[gl_DrawIDARB]+gl_InstanceID;
	if(indirect == 0) { entityBufferIndex = entityIndex + gl_InstanceID; }

	Entity entity = entities[entityBufferIndex];
	mat4 modelMatrix = entity.modelMatrix;

	#ifdef ANIMATED
	VertexAnimatedPacked vertex;
	#else
	VertexPacked vertex;
	#endif

	int vertexIndex = gl_VertexID;
	vertex = vertices[vertexIndex];

	vs_pass_WorldPosition = modelMatrix * vec4(vertex.position.xyz,1);

	vs_pass_Entity = entity;
	vs_pass_Material = materials[entity.materialIndex];

	if(entity.invertTexcoordY == 1) {
		vertex.texCoord.y = 1 - vertex.texCoord.y;
	}

	vs_pass_texCoord = vertex.texCoord.xy;
}