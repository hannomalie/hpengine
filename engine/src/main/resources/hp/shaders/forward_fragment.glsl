
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

layout(binding=8) uniform sampler2D directionalLightShadowMap;
#endif

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform float near = 0.1;
uniform float far = 100.0;
uniform vec3 color = vec3(0,0,0);

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

//include(globals_structs.glsl)

flat in VertexShaderFlatOutput vertexShaderFlatOutput;
in VertexShaderOutput vertexShaderOutput;

flat in Entity outEntity;
flat in Material outMaterial;

layout(location=0)out vec4 out_Color;
layout(location=1)out vec4 out_Revealage;

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _directionalLight {
	DirectionalLightState directionalLight;
};

//include(globals.glsl)
//include(normals.glsl)

//include(global_lighting.glsl)

void main()
{
	vec4 position_clip_post_w = vertexShaderOutput.position_clip/vertexShaderOutput.position_clip.w;
	Material material = vertexShaderFlatOutput.material;

    vec3 materialDiffuseColor = material.diffuse.rgb;
    float materialRoughness = material.roughness;
    float materialMetallic = material.metallic;
    float materialAmbient = material.ambient;
    float parallaxBias = material.parallaxBias;
    float parallaxScale = material.parallaxScale;
    float materialTransparency = material.transparency;

    vec4 color = vec4(materialDiffuseColor, 1);



#ifdef BINDLESSTEXTURES
    float visibility = getVisibility(vertexShaderOutput.position_world.xyz, directionalLight);

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

#else
    float visibility = getVisibility(vertexShaderOutput.position_world.xyz, directionalLight, directionalLightShadowMap);
#endif

	if(hasDiffuseMap) {
    	color = texture(diffuseMap, vertexShaderOutput.texCoord);
	}

    float opaqueness = 1-materialTransparency;

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;

	vec3 PN_world = normalize(vertexShaderOutput.normal_world);
	vec3 old_PN_world = PN_world;
	if(hasNormalMap) {
        PN_world = normalize(perturb_normal(old_PN_world, V, vertexShaderOutput.texCoord, normalMap));
    }

	int materialType = int(material.materialtype);
	int DEFAULT = 0;
	int FOLIAGE = 1;
	int UNLIT = 2;
	vec4 resultingColor = vec4(0,0,0,color.a * opaqueness);
	if(materialType == DEFAULT) {
        float NdotL = max(clamp(dot(-directionalLight.direction, PN_world), 0, 1), 0.01);
        resultingColor.rgb = color.rgb * directionalLight.color * NdotL * max(visibility, 0.0);
	    resultingColor.rgb = max(vec3(.005) * color.rgb, resultingColor.rgb);
	} else if(materialType == FOLIAGE) {
        float NdotL = max(clamp(dot(-directionalLight.direction, PN_world), 0, 1), 0.01);
        resultingColor.rgb = color.rgb * directionalLight.color * NdotL * max(visibility, 0.0);
	    resultingColor.rgb = max(vec3(.005) * color.rgb, resultingColor.rgb);
		resultingColor.rgb += color.rgb * directionalLight.color * clamp(dot(-directionalLight.direction, -PN_world), 0, 1);
	} else {
	    resultingColor.rgb = color.rgb * opaqueness;
	}

//Color based weighting, dunno yet if this makes sense for me
//https://github.com/lukexi/wboit/blob/master/test/renderPass.frag
    float weight = max(min(1.0, max(max(resultingColor.r, resultingColor.g), resultingColor.b) * color.a), color.a)
                 * clamp(0.03 / (1e-5 + pow(vertexShaderOutput.position_clip.z / 200, 4.0)), 1e-2, 3e3);

    const bool depthBasedWeight = true;
    if(depthBasedWeight) {
//        https://github.com/gnayuy/wboit/blob/master/fshader.glsl
        weight = pow(color.a + 0.01f, 4.0f) + max(0.01f, min(3000.0f, 0.3f / (0.00001f + pow(abs(gl_FragCoord.z) / 200.0f, 4.0f))));
//        http://jcgt.org/published/0002/02/09/paper.pdf
        weight = color.a * max(pow(10, -2), 3*pow(10,3)* pow((1-gl_FragCoord.z),3));

//        https://www.gdcvault.com/play/1025400/Rendering-Technology-in-Agents-of
        float a = min(8*color.a, 1) + 0.01;
        a = min(3*color.a + 2 * luminance(color.rgb), 1);
        const float k = 2;
        float b = min(3*color.a + k * luminance(color.rgb), 1);
        weight = min(pow(10, 4) * pow(b,3), 20) * pow(a,3);
    }

    out_Color = vec4(4*(resultingColor.rgb + color.rgb * materialAmbient) * weight, weight);
    out_Revealage.r = resultingColor.a;
    out_Revealage.a = min(10*luminance(color.rgb)*materialAmbient, 1);
}