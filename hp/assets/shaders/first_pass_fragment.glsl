#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=6) uniform samplerCube environmentMap;
layout(binding=7) uniform sampler2D lightMap;

uniform bool isSelected = false;

uniform int entityCount = 1;

uniform bool useParallax;
uniform bool useSteepParallax;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform float lightmapWidth;
uniform float lightmapHeight;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

uniform float sceneScale = 1f;
uniform float inverseSceneScale = 1f;
uniform int gridSize;

uniform bool useNormalMaps = true;

in vec4 color;
in vec2 texCoord;
in vec3 lightmapTextureCoord;
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
in mat3 TBN;
flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;
flat in int outEntityBufferIndex;
flat in int outMaterialIndex;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility
layout(location=5)out vec4 out_lightmap; // visibility

//include(globals.glsl)

mat3 cotangent_frame( vec3 N, vec3 p, vec2 uv )
{
	vec3 dp1 = dFdx( p );
	vec3 dp2 = dFdy( p );
	vec2 duv1 = dFdx( uv );
	vec2 duv2 = dFdy( uv );

	vec3 dp2perp = cross( dp2, N );
	vec3 dp1perp = cross( N, dp1 );
	vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;

	float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
	return mat3( T * invmax, B * invmax, N );
}
vec3 perturb_normal(vec3 N, vec3 V, vec2 texcoord, sampler2D normalMap)
{
	vec3 map = (texture(normalMap, texcoord)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}
void main(void) {

    int entityIndex = outEntityBufferIndex;
    Entity entity = outEntity;

	Material material = outMaterial;

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

    #define use_precomputed_tangent_space_
	if(useNormalMaps && material.hasNormalMap != 0) {
        #ifdef use_precomputed_tangent_space
            sampler2D _normalMap = sampler2D(uint64_t(material.handleNormal));
            PN_world = transpose(TBN) * normalize((texture(_normalMap, UV)*2-1).xyz);
        #else
            sampler2D _normalMap = sampler2D(uint64_t(material.handleNormal));
            PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
        #endif
        PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
    }


	vec2 uvParallax = vec2(0,0);
	if(material.hasHeightMap != 0) {
        sampler2D _heightMap = sampler2D(uint64_t(material.handleHeight));

		float height = (texture(_heightMap, UV).rgb).r;

        #ifdef use_precomputed_tangent_space
            vec3 viewVectorTangentSpace = normalize((TBN) * (V));
            float v = height * parallaxScale - parallaxBias;
        #else
            mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
            vec3 viewVectorTangentSpace = -normalize((TBN) * (V));
            float v = height * parallaxScale - parallaxBias;
            v = clamp(0, v, v);
        #endif
		uvParallax = (v * viewVectorTangentSpace.xy);
		UV = UV + uvParallax;
	}


	float depth = (position_clip.z / position_clip.w);

	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);

	vec4 color = vec4(materialDiffuseColor, 1);
    float alpha = materialTransparency;
	if(material.hasDiffuseMap != 0) {
        sampler2D _diffuseMap = sampler2D(uint64_t(material.handleDiffuse));

    	color = texture(_diffuseMap, UV);
    	//color = textureLod(_diffuseMap, UV, 6);
        alpha *= color.a;
        if(color.a<0.1)
        {
            discard;
        }
	}
  	out_color = color;
  	out_color.w = float(materialMetallic);

//    vec2 finalLightMapCoords = scaleLightmapCoords(lightmapTextureCoord, lightmapWidth, lightmapHeight);
//    out_lightmap.rg = finalLightMapCoords;

	if(material.hasOcclusionMap != 0) {
	    //out_color.rgb = clamp(out_color.rgb - texture2D(occlusionMap, UV).xyz, 0, 1);
	}

	out_position.w = materialRoughness;
	if(material.hasRoughnessMap != 0) {
        sampler2D _roughnessMap = sampler2D(uint64_t(material.handleRoughness));
        float r = texture(_roughnessMap, UV).x;
        out_position.w = materialRoughness*r;
    }

	if(material.hasSpecularMap != 0) {
//	UV.x = texCoord.x * specular;
//	UV.y = texCoord.y * specular;
//	UV = texCoord + uvParallax;
//	vec3 specularSample = texture2D(specularMap, UV).xyz;
//	float glossiness = length(specularSample)/length(vec3(1,1,1));
//	const float glossinessBias = 1.5;
//	out_position.w = clamp(glossinessBias-glossiness, 0, 1) * (materialRoughness);
    }

  	out_motion = vec4(motionVec,depth,materialTransparency);
  	out_normal.a = materialAmbient;
  	out_visibility = vec4(1,depth,outMaterialIndex, float(outEntity.entityIndex));

  	if(RAINEFFECT) {
		float n = surface3(vec3(UV, 0.01));
		float n2 = surface3(vec3(UV, 0.1), 2);
		float waterEffect = rainEffect * clamp(3 * n2 * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0)*clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		float waterEffect2 = rainEffect * clamp(3 * n * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		out_position.w *= 1-waterEffect2;
		out_color.rgb *= mix(vec3(1,1,1), vec3(1,1,1+waterEffect/8), waterEffect2);
		out_color.w = waterEffect2;
  	}
  	
	if(isSelected)
	{
		out_color.rgb = vec3(1,0,0);
	}
	//out_color.rgb = vec3(1,0,0);
//	out_color.rgb = position_world.rgb/100.0;
	//out_normal.a = 1;
}
