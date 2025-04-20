#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D displacementMap;
uniform bool hasDisplacementMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif
layout(binding=6) uniform samplerCube environmentMap;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform vec3 eyePosition;

uniform int time;

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

layout(location=0)out vec4 out_color;

//include(globals.glsl)
//include(normals.glsl)

void main(void) {

    int entityIndex = vertexShaderFlatOutput.entityBufferIndex;
    Entity entity = vertexShaderFlatOutput.entity;
	int entityId = entity.entityIndexWithoutMeshIndex;

	Material material = vertexShaderFlatOutput.material;

	vec4 position_world = vertexShaderOutput.position_world;
	vec4 position_clip = vertexShaderOutput.position_clip;
	vec3 normal_world = vertexShaderOutput.normal_world;

	vec3 V = -normalize((position_world.xyz + eyePosition.xyz).xyz);
	vec2 UV = vertexShaderOutput.texCoord * material.uvScale;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

	vec4 color = vec4(material.diffuse, 1); // TODO: Make material configurable for per vertex colors
	vec2 uvParallax = vec2(0,0);

#ifdef BINDLESSTEXTURES

	sampler2D diffuseMap;
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) { diffuseMap = sampler2D(material.handleDiffuse); }

	sampler2D heightMap;
	bool hasHeightMap = uint64_t(material.handleHeight) > 0;
	if(hasHeightMap) { heightMap = sampler2D(material.handleHeight); };

#endif

	if(hasHeightMap) {
		float height = texture(heightMap, UV).r;

		mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
		vec3 viewVectorTangentSpace = -normalize((TBN) * (V));
		float v = height * material.parallaxScale - material.parallaxBias;
		v = clamp(0, v, v);

		uvParallax = (v * viewVectorTangentSpace.xy);
		vec3 viewPositionTanget = TBN * eyePosition;
		vec3 fragmentPositionTangent = TBN * position_world.xyz;
		vec3 viewDirTanget = normalize(viewPositionTanget - fragmentPositionTangent);
		//		UV = parallaxMapping(heightMap, UV, viewDirTanget, material.parallaxScale, material.parallaxBias);
		UV = UV + uvParallax;
	}
	if(hasDiffuseMap) {
		// https://www.reddit.com/r/opengl/comments/3cdg5r/derivation_of_opengls_mipmap_level_computation/
//		vec2  dx_vtc        = dFdx(UV);
//		vec2  dy_vtc        = dFdy(UV);
//		float delta_max_sqr = max(dot(dx_vtc, dx_vtc), dot(dy_vtc, dy_vtc));
//		color = textureLod(diffuseMap, UV, material.diffuseMipmapBias + delta_max_sqr);
		color = texture(diffuseMap, UV, material.diffuseMipmapBias);
	}
	if(color.a < 0.9) {
		discard;
	}
	out_color.rgba = vec4(color.rgb * color.a, 1);
	//END
}
