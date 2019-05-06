
#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
#endif
layout(binding=6) uniform samplerCube environmentMap;

uniform bool isSelected = false;

uniform bool useParallax;
uniform bool useSteepParallax;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

uniform vec3 eyePosition;

uniform bool useNormalMaps = true;

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_positionRoughness;
layout(location=1)out vec4 out_normalAmbient;
layout(location=2)out vec4 out_colorMetallic;
layout(location=3)out vec4 out_motionDepthTransparency;
layout(location=4)out vec4 out_depthAndIndices;

//include(globals.glsl)

void main(void) {

    int entityIndex = vertexShaderFlatOutput.entityBufferIndex;
    Entity entity = vertexShaderFlatOutput.entity;

	Material material = vertexShaderFlatOutput.material;

	vec4 position_world = vertexShaderOutput.position_world;
	vec4 position_clip = vertexShaderOutput.position_clip;
	vec4 position_clip_last = vertexShaderOutput.position_clip_last;
	vec3 normal_world = vertexShaderOutput.normal_world;

	vec3 V = -normalize((position_world.xyz + eyePosition.xyz).xyz);
	vec2 UV = vertexShaderOutput.texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 motionVec = (position_clip_post_w.xy) - (position_clip_last_post_w.xy);

//	motionVec.x = length(distance(position_clip_last.xyz, position_clip.xyz));

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

	vec2 positionTextureSpace = position_clip_post_w.xy * 0.5 + 0.5;

	out_positionRoughness = viewMatrix * position_world;

	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
	vec3 old_PN_world = PN_world;

	vec4 color = vec4(material.diffuse, 1);

	#ifdef BINDLESSTEXTURES
		if(useNormalMaps && uint64_t(material.handleNormal) > 0) {
			sampler2D _normalMap = sampler2D((material.handleNormal));
			PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
			PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
		}


		vec2 uvParallax = vec2(0,0);
		if(uint64_t(material.handleHeight) > 0) {
			sampler2D _heightMap = sampler2D((material.handleHeight));

			float height = (texture(_heightMap, UV).rgb).r;

			mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
			vec3 viewVectorTangentSpace = -normalize((TBN) * (V));
			float v = height * material.parallaxScale - material.parallaxBias;
			v = clamp(0, v, v);

			uvParallax = (v * viewVectorTangentSpace.xy);
			vec3 viewPositionTanget = TBN * eyePosition;
			vec3 fragmentPositionTangent = TBN * position_world.xyz;
			vec3 viewDirTanget = normalize(viewPositionTanget - fragmentPositionTangent);
			//		UV = parallaxMapping(_heightMap, UV, viewDirTanget, material.parallaxScale, material.parallaxBias);
			UV = UV + uvParallax;
		}


		float depth = (position_clip.z / position_clip.w);

		out_normalAmbient = vec4(PN_view, materialAmbient);

		float alpha = material.transparency;
		if(uint64_t(material.handleDiffuse) > 0) {
			sampler2D _diffuseMap = sampler2D(material.handleDiffuse);

			color = texture(_diffuseMap, UV);
			alpha *= color.a;
			if(color.a<0.1)
			{
				discard;
			}
		}
		out_colorMetallic = color;
		out_colorMetallic.w = material.metallic;

		if(uint64_t(material.handleOcclusion) > 0) {
			//out_color.rgb = clamp(out_color.rgb - texture2D(occlusionMap, UV).xyz, 0, 1);
		}

		out_positionRoughness.w = material.roughness;
		if(uint64_t(material.handleRoughness) != 0) {
			sampler2D _roughnessMap = sampler2D((material.handleRoughness));
			float r = texture(_roughnessMap, UV).x;
			out_positionRoughness.w = material.roughness*r;
		}


		if(uint64_t(material.handleSpecular) > 0) {
			//	UV.x = texCoord.x * specular;
			//	UV.y = texCoord.y * specular;
			//	UV = texCoord + uvParallax;
			//	vec3 specularSample = texture2D(specularMap, UV).xyz;
			//	float glossiness = length(specularSample)/length(vec3(1,1,1));
			//	const float glossinessBias = 1.5;
			//	out_position.w = clamp(glossinessBias-glossiness, 0, 1) * (material.roughness);
		}

		out_motionDepthTransparency = vec4(motionVec,depth,material.transparency);
		out_depthAndIndices = vec4(float(outEntity.entityIndexWithoutMeshIndex), depth, outMaterialIndex, float(outEntity.meshIndex));

		if(RAINEFFECT) {
			float n = surface3(vec3(UV, 0.01));
			float n2 = surface3(vec3(UV, 0.1), 2);
			float waterEffect = rainEffect * clamp(3 * n2 * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0)*clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
			float waterEffect2 = rainEffect * clamp(3 * n * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
			out_positionRoughness.w *= 1-waterEffect2;
			out_colorMetallic.rgb *= mix(vec3(1,1,1), vec3(1,1,1+waterEffect/8), waterEffect2);
			out_colorMetallic.w = waterEffect2;
		}

		//	out_colorMetallic.rgb = vec3(1,0,0);
		//	out_colorMetallic.rgb = position_world.rgb/100.0;
		//out_normal.a = 1;
	#else
		if(hasDiffuseMap) {
			color = texture(diffuseMap, UV);
		}
	#endif

	if(color.a<0.1)
	{
		discard;
	}
	if(isSelected)
	{
		color.rgb = vec3(1,0,0);
	}
	out_colorMetallic = vec4(color.rgb, material.metallic);

}
