
layout(binding=0) uniform sampler2D diffuseMap;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform float near = 0.1;
uniform float far = 100.0;
uniform vec3 color = vec3(0,0,0);
uniform bool hasDiffuseMap;

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

//include(globals_structs.glsl)

in vec4 pass_Position;
in vec4 pass_WorldPosition;
in vec3 normal_world;
in vec2 texCoord;
in vec4 position_clip;

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

//include(global_lighting.glsl)

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
vec2 parallaxMapping(sampler2D heightMap, vec2 texCoords, vec3 viewDir, float height_scale, float parallaxBias)
{
    float height =  texture(heightMap, texCoords).r;
    if(height < parallaxBias) {
        height = 0;
    };
    vec2 p = viewDir.xy / viewDir.z * (height * height_scale);
    return texCoords - p;
}
void main()
{
	vec2 UV = texCoord;
	vec4 position_clip_post_w = position_clip/position_clip.w;
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

    vec4 color = vec4(materialDiffuseColor, 1);
	if(uint64_t(material.handleDiffuse) > 0) {
        sampler2D _diffuseMap = sampler2D(material.handleDiffuse);

    	color = texture(_diffuseMap, UV);
	}

    float opaqueness = 1-materialTransparency;

	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;

//	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
	vec3 old_PN_world = PN_world;
	if(uint64_t(material.handleNormal) > 0) {
        sampler2D _normalMap = sampler2D((material.handleNormal));
        PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
//        PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
    }

	int materialType = int(material.materialtype);
	int DEFAULT = 0;
	int FOLIAGE = 1;
	int UNLIT = 2;
	vec4 resultingColor = vec4(0,0,0,color.a * opaqueness);
	if(materialType == DEFAULT) {
        float NdotL = max(clamp(dot(-directionalLight.direction, PN_world), 0, 1), 0.01);
        float visibility = getVisibility(pass_WorldPosition.xyz, directionalLight);
        resultingColor.rgb = color.rgb * directionalLight.color * NdotL * max(visibility, 0.0);
	    resultingColor.rgb = max(vec3(.005) * color.rgb, resultingColor.rgb);
	} else if(materialType == FOLIAGE) {
        float NdotL = max(clamp(dot(-directionalLight.direction, PN_world), 0, 1), 0.01);
        float visibility = getVisibility(pass_WorldPosition.xyz, directionalLight);
        resultingColor.rgb = color.rgb * directionalLight.color * NdotL * max(visibility, 0.0);
	    resultingColor.rgb = max(vec3(.005) * color.rgb, resultingColor.rgb);
		resultingColor += color.rgb * directionalLight.color * clamp(dot(-directionalLight.direction, -PN_world), 0, 1);
	} else {
	    resultingColor.rgb = color.rgb;
	}

//Color based weighting, dunno yet if this makes sense for me
//https://github.com/lukexi/wboit/blob/master/test/renderPass.frag
    float weight = max(min(1.0, max(max(resultingColor.r, resultingColor.g), resultingColor.b) * color.a), color.a)
                 * clamp(0.03 / (1e-5 + pow(pass_Position.z / 200, 4.0)), 1e-2, 3e3);

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
    out_Revealage.a = min(10*luminance(4*color.rgb*materialAmbient), 1);
}