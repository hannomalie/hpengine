#define WORK_GROUP_SIZE 4

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
//layout(binding=0) uniform isampler2D triangleIdTexture;
layout(binding=0) uniform sampler2D triangleIdTexture;
layout(binding=1) uniform sampler2D barycentricsTexture;
layout(binding=2) uniform sampler2DArray diffuseTextures;
layout(binding=3) uniform sampler2D mipMapLevelTexture;

layout(binding=2, rgba8) uniform image2D out_color;

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _entities {
	Entity entities[1000];
};
layout(std430, binding=3) buffer _vertices {
	VertexPacked vertices[100000];
};

uniform int width = 1920;
uniform int height = 1080;

//include(globals.glsl)

void main(void) {
	ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
	ivec2 workGroup = ivec2(gl_WorkGroupID);
	ivec2 workGroupSize = ivec2(gl_WorkGroupSize.xy);
	ivec2 localIndex = ivec2(gl_LocalInvocationID.xy);
	ivec2 size = ivec2(width, height);
	vec2 st = vec2(storePos) / vec2(size);

	ivec4 triangleIdTextureSample = ivec4(textureLod(triangleIdTexture, st, 0));
	//uint triangleId = textureLod(triangleIdTexture, st, 0).r;
	uint triangleId = uint(triangleIdTextureSample.r);
	float mipMapLevel = textureLod(mipMapLevelTexture, st, 0).r;
	vec4 barycentricsSample = textureLod(barycentricsTexture, st, 0);
	int entityId = int(barycentricsSample.a);
	Entity entity = entities[entityId];
	Material material = materials[entity.materialIndex];

	vec3 barycentrics = barycentricsSample.rgb;

	vec3 color = material.diffuse;

//	VertexPacked a = vertices[triangleId * 3 + 0];
//	VertexPacked b = vertices[triangleId * 3 + 1];
//	VertexPacked c = vertices[triangleId * 3 + 2];

//	triangleId = triangleIdTextureSample.y;
//	VertexPacked a = vertices[triangleId];
//	VertexPacked b = vertices[triangleId + 1];
//	VertexPacked c = vertices[triangleId + 2];

	VertexPacked a = vertices[triangleIdTextureSample.g];
	VertexPacked b = vertices[triangleIdTextureSample.b];
	VertexPacked c = vertices[triangleIdTextureSample.a];

	vec2 uv = barycentrics.x * a.texCoord.xy + barycentrics.y * b.texCoord.xy + barycentrics.z * c.texCoord.xy;
	uv *= material.uvScale;
	uv = vec2(1) - uv;

#ifdef BINDLESSTEXTURES
	sampler2D diffuseMap;
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) {
		diffuseMap = sampler2D(material.handleDiffuse);
		color.rgb = textureLod(diffuseMap, uv, mipMapLevel).rgb;
	}

	sampler2D heightMap;
	bool hasHeightMap = uint64_t(material.handleHeight) > 0;
	if(hasHeightMap) { heightMap = sampler2D(material.handleHeight); };
#else
	color.rgb = textureLod(diffuseTextures, vec3(uv, material.diffuseMapIndex), mipMapLevel).rgb;
#endif

	if(triangleId == 0) {
		color.rgb = vec3(1,0,0);
	}
	imageStore(out_color, storePos, vec4(color, 1));
//	imageStore(out_color, storePos, vec4(uv, 0, 1));
//	imageStore(out_color, storePos, vec4(barycentrics, 1));
}
