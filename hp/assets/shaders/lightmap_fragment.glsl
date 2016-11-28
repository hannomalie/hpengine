#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=6) uniform sampler2D shadowMap;

uniform sampler2D renderedTexture;
uniform vec3 diffuseColor = vec3(1,0,0);

uniform vec3 lightDirection = vec3(1,0,0);
uniform vec3 lightDiffuse = vec3(1,0,0);

uniform mat4 shadowMatrix;

//include(globals_structs.glsl)

in vec2 texCoord;
in vec3 normal_world;
in vec4 position_world;
in vec2 lightmapTexCoord;

flat in Entity outEntity;
flat in Material outMaterial;
flat in int outEntityIndex;
flat in int outEntityBufferIndex;
flat in int outMaterialIndex;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_albedo;
layout(location=3)out vec4 out_color;
layout(location=4)out vec4 out_indirect;

vec3 getVisibility(float dist, vec4 ShadowCoordPostW)
{
  	if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
		return vec3(0,0,0);
	}

	// We retrive the two moments previously stored (depth and depth*depth)
	vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 0);
	vec2 moments = shadowMapSample.rg;
	vec2 momentsUnblurred = moments;


	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist - 0.001f <= moments.x) {
		return vec3(1.0,1.0,1.0);
	}
	else { return vec3(0); }
}

void main()
{
	vec2 UV = texCoord;
    int entityIndex = outEntityBufferIndex;
    Entity entity = outEntity;

	Material material = outMaterial;

    vec3 materialDiffuseColor = vec3(material.diffuseR,
                                     material.diffuseG,
                                     material.diffuseB);


	vec4 color = vec4(materialDiffuseColor, 1);
	float alpha;
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

    out_color = vec4(color.rgb * lightDiffuse.rgb ,1);

	vec4 positionShadow = (shadowMatrix * vec4(position_world.xyz, 1));
	vec3 positionInCameraSpace = positionShadow.xyz;
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	float visibility = clamp(getVisibility(depthInLightSpace, positionShadow), 0.0f, 1.0).r;
    out_color.rgb = lightDiffuse*vec3(clamp(dot(normal_world, lightDirection), 0.f, 1.f) * visibility);

    out_color.rgb += vec3(float(material.ambient));
//    out_color.rgb += color.rgb;
//    out_color += color;
//    out_color = vec4(lightmapTexCoord, 0, 1);
    //out_color = vec4(position_world.xyz/100f, 1);
//    out_color *= 4f;
    out_color.a = 1;

    out_position.rgb = position_world.xyz;
    out_position.a = 1;
    out_normal.rgb = normal_world;
    out_albedo.rgb = color.rgb;
}
