
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

//include(globals_structs.glsl)

#ifdef PROGRAMMABLE_VERTEX_PULLING
layout(std430, binding=7) buffer _vertices {
	VertexPacked vertices[];
};
#endif

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

out vec4 pass_Position;
out vec4 pass_WorldPosition;
out vec3 normal_world;
out vec2 texCoord;
out vec3 normal_view;

void main()
{

	VertexPacked vertex;

#ifdef PROGRAMMABLE_VERTEX_PULLING
	int vertexIndex = gl_VertexID;
	vertex = vertices[vertexIndex];
	vertex.position.w = 1;
#else
	vertex.position = vec4(in_Position.xyz,1);
	vertex.normal = vec4(in_Normal, 0);
	vertex.texCoord = vec4(in_TextureCoord, 0, 0);
#endif

	pass_WorldPosition = modelMatrix * vertex.position;
	pass_Position = projectionMatrix * viewMatrix * pass_WorldPosition;
    gl_Position = pass_Position;
	normal_world.x = dot(modelMatrix[0].xyz, vertex.normal.xyz);
    normal_world.y = dot(modelMatrix[1].xyz, vertex.normal.xyz);
    normal_world.z = dot(modelMatrix[2].xyz, vertex.normal.xyz);
	normal_world = (inverse(transpose(modelMatrix)) * vertex.normal).xyz;
	//normal_world = modelMatrix * vec4(in_Normal,0)).xyz;
	normal_view = (viewMatrix * vec4(normal_world.xyz, 0)).xyz;
	
	texCoord = vertex.texCoord.xy;
	texCoord.y = 1 - in_TextureCoord.y;
}
