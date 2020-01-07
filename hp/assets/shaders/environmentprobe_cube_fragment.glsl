//include(globals_structs.glsl)

uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};
layout(std430, binding=6) buffer _directionalLightState {
	DirectionalLightState directionalLight;
};

#ifdef BINDLESSTEXTURES
#else
layout(binding=8) uniform sampler2D directionalLightShadowMap;
#endif
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

#endif
layout(binding=8) uniform samplerCube skyBox;

layout(std430, binding=5) buffer _probeAmbientCubeValuesOld {
	vec4 probeAmbientCubeValuesOld[];
};

uniform vec3 pointLightPositionWorld;
uniform vec3 sceneMin = vec3(0);
uniform vec3 probesPerDimension = vec3(4);
uniform vec3 probeDimensions = vec3(50);

in vec4 pass_WorldPosition;
in vec4 pass_ProjectedPosition;
in float clip;
in vec2 pass_texCoord;
in vec3 pass_normal;
flat in Entity pass_Entity;
flat in Material pass_Material;

out vec4 out_Color;
out vec4 out_Diffuse;
out vec4 out_Position;

//include(globals.glsl)
//include(global_lighting.glsl)


float calculateAttenuation(float dist, float lightRadius) {
	float distDivRadius = (dist / lightRadius);
	float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
	atten_factor = pow(atten_factor, 2);
	return atten_factor;
}

const vec3[6] directions = {
	vec3(1, 0, 0),
	vec3(-1, 0, 0),
	vec3(0, 1, 0),
	vec3(0, -1, 0),
	vec3(0, 0, 1),
	vec3(0, 0, -1)
};

void main()
{

    if(clip < 0.0f) {
		discard;
	}

	Material material = pass_Material;
	vec4 color = vec4(material.diffuse.rgb, 1.0f);
	vec2 UV = pass_texCoord;
	float alpha = material.transparency;

#ifdef BINDLESSTEXTURES
	sampler2D diffuseMap;
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) { diffuseMap = sampler2D(material.handleDiffuse); }
#endif

	if(hasDiffuseMap) {
		color = texture(diffuseMap, UV);
		alpha *= color.a;
	}

	float depth = pass_ProjectedPosition.z/pass_ProjectedPosition.w;

    float lightDistance = distance(pass_WorldPosition.xyz, pointLightPositionWorld);
//    lightDistance = lightDistance / 250.0;
    depth = lightDistance;
//    gl_FragDepth = lightDistance;

	float moment1 = (depth);
	float moment2 = moment1 * moment1;
	float ambient =  material.ambient;
	vec4 result = color * ambient;
	vec3 normal = pass_normal;

	vec3 V = vec3(0,0,-1);
	vec3 specularColor = color.rgb;
	float roughness = 1.0f;
	float metallic = 0.0f;

	for (uint lightIndex = 0; lightIndex < pointLightCount; ++lightIndex)
	{
		PointLight pointLight = pointLights[lightIndex];
		vec3 pointLightPosition = pointLight.position;
		float rad = 2*float(pointLight.radius);

		if (distance(pointLight.position, pass_WorldPosition.xyz) < rad) {

			vec3 lightDiffuse = pointLight.color;
			vec3 lightDirection = normalize(vec4(pointLight.position - pass_WorldPosition.xyz, 0)).xyz;
			float attenuation = calculateAttenuation(length(pointLight.position - pass_WorldPosition.xyz), float(pointLight.radius));

			vec3 temp;

			if(int(material.materialtype) == 1) {
				temp = cookTorrance(lightDirection, lightDiffuse,
				attenuation, V, pass_WorldPosition.xyz, normal,
				roughness, 0, color.rgb, specularColor);
				temp = attenuation * lightDiffuse * color.rgb * clamp(dot(-normal, lightDirection), 0, 1);
			} else {
				temp = cookTorrance(lightDirection, lightDiffuse,
				attenuation, V, pass_WorldPosition.xyz, normal,
				roughness, metallic, color.rgb, specularColor);
			}
			temp = color.rgb * lightDiffuse * attenuation;
			result.rgb += temp.rgb;
		}
	}

	#if BINDLESS_TEXTURES
	float visibility = getVisibility(pass_WorldPosition, directionalLight);
	#else
	float visibility = getVisibility(pass_WorldPosition.xyz, directionalLight, directionalLightShadowMap);
	#endif
//	TODO: Enable directional light stuff
	//result.rgb += visibility * cookTorrance(-directionalLight.direction, directionalLight.color, 1.0f, V, pointLightPositionWorld, normal, roughness, metallic, color.rgb, vec3(0));


	ivec3 probeIndexOffsets = ivec3(pass_WorldPosition.xyz-sceneMin)/ivec3(probeDimensions);
	ivec3 probesPerDimensionInt = ivec3(probesPerDimension);
	int resultingProbeIndex = probesPerDimensionInt.x * probeIndexOffsets.x
		+ probesPerDimensionInt.y * probeIndexOffsets.y
		+ probesPerDimensionInt.z * probeIndexOffsets.z;

	//	float mipMap = float(textureQueryLevels(probeCubeMaps)-2); // TODO: Figure out why -2
	//	result.rgb += textureLod(probeCubeMaps, vec4(normalWorld, resultingProbeIndex), mipMap).rgb;

//	TODO: second bounce
//	int baseProbeIndex = resultingProbeIndex*6;
//	for(int faceIndex = 0; faceIndex < 6; faceIndex++) {
//		float dotProd = clamp(dot(normal, directions[faceIndex]), 0, 1);
//		result.rgb += dotProd * probeAmbientCubeValuesOld[baseProbeIndex+faceIndex].rgb;
//	}

	out_Color = 4.0f * result;
	//	out_Color = vec4(UV, 0.0f, 1.0f);
//	vec3 sampleToProbe = pass_WorldPosition.xyz - pointLightPositionWorld;
//	vec3 skyBoxSample = textureLod(skyBox, sampleToProbe, 0).rgb;
//	out_Color = vec4(skyBoxSample, 1.0f);

//	if(gl_Layer == 0) {
//	    out_Color.r = 1;
//	} else if(gl_Layer == 1) {
//	    out_Color.g = 1;
//	}
//	out_Color = vec4(gl_FragCoord.xy/vec2(512,512),0,0);

//	float dx = dFdx(depth);
//	float dy = dFdy(depth);
	//moment2 += 0.25*(dx*dx+dy*dy);
    //out_Color = vec4(moment1,moment2,packColor(normal_world),1);
    
//    out_Color = vec4(moment1,moment2,0,0);//encode(normal_world));
//    out_Color.rgba = vec4(1,0,0,1);
    //out_Diffuse = vec4(normal_world,1);
    /*vec3 diffuse = color;
    out_Diffuse = vec4(diffuse,1);
    out_Position = vec4(pass_WorldPosition.xyz, 0);*/
    //out_Color = vec4(moment1,moment2,encode(normal_world));
}