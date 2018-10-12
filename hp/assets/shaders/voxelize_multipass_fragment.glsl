layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
//layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;
layout(binding=7) uniform sampler2D roughnessMap;

uniform layout(binding = 5, rgba16f) image3D out_voxel;
//uniform int entityIndex;
uniform int materialIndex;
uniform bool isSelected = false;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec3 tangent_world;
in vec3 bitangent_world;
in vec4 position_clip;
in vec4 position_clip_last;
//in vec4 position_clip_uv;
//in vec4 position_clip_shadow;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;
flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility

//include(globals.glsl)

void main(void) {

    int entityIndex = outEntityIndex;
    Entity entity = outEntity;//entities[entityIndex];
//    bool isSelected = entity.isSelected != 0.0 ? true : false;
//    int materialIndex = int(entity.materialIndex);

//	Material material = outMaterial;
	Material material = materials[materialIndex];
	vec3 materialDiffuseColor = vec3(material.diffuseR,
									 material.diffuseG,
									 material.diffuseB);
	float materialRoughness = float(material.roughness);
	float materialMetallic = float(material.metallic);
	float materialAmbient = float(material.ambient);
	float parallaxBias = float(material.parallaxBias);
	float parallaxScale = float(material.parallaxScale);
	float materialTransparency = float(material.transparency);


	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	//V = normalize((eyePos_world.xyz - position_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 motionVec = (position_clip_post_w.xy) - (position_clip_last_post_w.xy);

//	motionVec.x = length(distance(position_clip_last.xyz, position_clip.xyz));

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;
	
	vec2 positionTextureSpace = position_clip_post_w.xy * 0.5 + 0.5;

	out_position = viewMatrix * position_world;

	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
	vec3 old_PN_world = PN_world;

	if(material.hasNormalMap != 0) {
        sampler2D _normalMap = sampler2D(uint64_t(material.handleNormal));
        PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
        PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
    }


	vec2 uvParallax = vec2(0,0);

	float depth = (position_clip.z / position_clip.w);

	out_normal = vec4(PN_view, depth);

	vec4 color = vec4(materialDiffuseColor, 1);

	if(material.hasDiffuseMap != 0) {

        color = texture(sampler2D(uint64_t(material.handleDiffuse)), UV);

        if(color.a<0.1)
        {
//            discard;
        }
	}

//    if(imageLoad(out_voxel, ivec3(position_world.xyz + ivec3(128))).a < 1.0) {
	    imageStore(out_voxel, ivec3(position_world.xyz + ivec3(128)), vec4(out_color.rgb,1));
//    }
}
