#define WORK_GROUP_SIZE 4

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE) in;
layout(binding=0) uniform sampler2D visibilityTexture;
layout(binding=2) uniform sampler2DArray diffuseTextures;

layout(binding=3, rgba8) uniform image2D out_color;

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

	vec4 visibilitySample = textureLod(visibilityTexture, st, 0);
	vec2 uv = visibilitySample.rg;
	float mipMapLevel = visibilitySample.b;
	int entityId = int(visibilitySample.a);

	Entity entity = entities[entityId];
	Material material = materials[entity.materialIndex];


	vec3 color = material.diffuse;


#ifdef BINDLESSTEXTURES
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) {
	    sampler2D diffuseMap  = sampler2D(material.handleDiffuse);
		color.rgb = textureLod(diffuseMap, uv, mipMapLevel).rgb;
	}
#else
	color.rgb = textureLod(diffuseTextures, vec3(uv, material.diffuseMapIndex), mipMapLevel).rgb;
#endif
	imageStore(out_color, storePos, vec4(color, 1));
}
