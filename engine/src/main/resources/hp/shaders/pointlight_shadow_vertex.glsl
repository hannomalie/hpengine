
uniform int indirect = 1;
uniform int entityIndex = 0;

uniform vec3 pointLightPositionWorld;
uniform float pointLightRadius;

uniform bool isBack;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
#ifdef ANIMATED
in vec4 in_Weights;
in ivec4 in_JointIndices;
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

out vec4 pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out vec2 texCoord;
out float clip;

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

	pass_WorldPosition = modelMatrix * vec4(vertex.position.xyz,1);

	pass_ProjectedPosition.xyz = pass_WorldPosition.xyz - pointLightPositionWorld;
	if(isBack) { pass_ProjectedPosition.z = -pass_ProjectedPosition.z; }

	float L = length(pass_ProjectedPosition.xyz);
	pass_ProjectedPosition /= L;
	clip = pass_ProjectedPosition.z;
	pass_ProjectedPosition.z = pass_ProjectedPosition.z + 1;
	pass_ProjectedPosition.x = pass_ProjectedPosition.x / pass_ProjectedPosition.z;
	pass_ProjectedPosition.y = pass_ProjectedPosition.y / pass_ProjectedPosition.z;
	const float NearPlane = 0.0001;
	float FarPlane = pointLightRadius;
	pass_ProjectedPosition.z = (L - NearPlane) / (FarPlane - NearPlane);
    pass_ProjectedPosition.w = 1;

    gl_Position = pass_ProjectedPosition;

	texCoord = vertex.texCoord.xy;
	texCoord.y = 1 - vertex.texCoord.y;
}