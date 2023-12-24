
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=9) uniform sampler2D aoBentNormalsMap;
layout(binding=10) uniform sampler2D depthAndIndicesMap;

#ifdef BINDLESSTEXTURES
#else
layout(binding=8) uniform sampler2D directionalLightShadowMap;
layout(binding=11) uniform sampler2D directionalLightStatcShadowMap;
#endif

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

layout(std430, binding=2) buffer _directionalLightState {
	DirectionalLightState directionalLight;
};

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 eyePosition;

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_DiffuseSpecular;

//include(globals.glsl)

//include(global_lighting.glsl)

vec3 blurESM(sampler2D sampler, vec2 texCoords, float dist, float inBlurDistance) {
	float darknessFactor = 120.0;
	vec3 result = vec3(0,0,0);
	float blurDistance = clamp(inBlurDistance, 0.0, 0.002);
	const int N = 32;
	const float bias = 0.001;
	for (int i = 0; i < N; i++) {
		float moment = texture(sampler, texCoords + (hammersley2d(i, N)-0.5)/100).x;
		result += clamp(exp(darknessFactor * (moment - dist)), 0.0, 1.0);
	}
	return result/N;
}

vec2 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
	projectedCoord.xy /= projectedCoord.w;
	projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
	return projectedCoord.xy;
}

const uint  g_sss_steps            = 8;     // Quality/performance
const float g_sss_ray_max_distance = 10.0f; // Max shadow length
const float g_sss_tolerance        = 0.5f; // Error in favor of reducing gaps
const float g_sss_step_length      = g_sss_ray_max_distance / float(g_sss_steps);

float ScreenSpaceShadows(vec3 positionView, vec3 lightDirectionView)
{
	// Compute ray position and direction (in view-space)
	vec3 ray_pos = positionView;
	vec3 ray_dir = lightDirectionView;

	// Compute ray step
	vec3 ray_step = ray_dir * g_sss_step_length;

	// Ray march towards the light
	float occlusion = 0.0;
	for (uint i = 0; i < g_sss_steps; i++)
	{
		// Step the ray
		ray_pos += ray_step;

		// Compute the difference between the ray's and the camera's depth
		vec2 ray_uv = getViewPosInTextureSpace(ray_pos);
		vec3 rayPositionView = textureLod(positionMap, ray_uv, 0).xyz;
		float depth_z = rayPositionView.z;
		float depth_delta = ray_pos.z - depth_z;

		// If the ray is behind what the camera "sees" (positive depth_delta)
		if (abs(g_sss_tolerance - depth_delta) < g_sss_tolerance)
//		if ( (0.0 < depth_delta) && (depth_delta < g_sss_tolerance) )
		{
			// Consider the pixel to be shadowed/occluded
			occlusion = 1.0f;
			break;
		}
	}

	// Fade out as we approach the edges of the screen
//	occlusion *= screen_fade(ray_uv);

	return 1.0f - occlusion;
}

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	float depth = textureLod(visibilityMap, st, 0).g;
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
	//vec4 positionViewPreW = (inverse(projectionMatrix)*vec4(st, depth, 1));
	//positionView = positionViewPreW.xyz / positionViewPreW.w;
  	
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 color = textureLod(diffuseMap, st, 0).xyz;
	float roughness = textureLod(positionMap, st, 0).a;
	
  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	V = positionView;

	// skip background
	if (positionView.z > -0.0001) {
//	  discard;
	}
	vec4 normalAmbient = textureLod(normalMap, st, 0);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView,0.0)).xyz;
	
	float metallic = textureLod(diffuseMap, st, 0).a;
	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, roughness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

	vec3 lightDirectionView = (viewMatrix * vec4(-directionalLight.direction, 0)).xyz;
	vec3 finalColor = vec3(1);

	int materialIndex = int(textureLod(visibilityMap, st, 0).b);
	Material material = materials[materialIndex];
	int materialType = int(material.materialtype);
	int DEFAULT = 0;
	int FOLIAGE = 1;
	int UNLIT = 2;
	vec3 lightDiffuse = directionalLight.color;

#ifdef BINDLESSTEXTURES
	float visibility = getVisibility(positionWorld, directionalLight);
#else
	float visibility = getVisibility(positionWorld.xyz, directionalLight, directionalLightShadowMap, directionalLightStatcShadowMap);
#endif

	vec4 aoBentNormals = textureLod(aoBentNormalsMap, st, 0);
	vec3 bentNormal = aoBentNormals.gba;

//	float ssdo = 1-ScreenSpaceShadows(positionView, lightDirectionView);
//	visibility = min(visibility, ssdo);

	if(materialType == FOLIAGE) {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse,
									1, V, positionView, normalView,
									roughness, 0, diffuseColor, specularColor);
//		finalColor += diffuseColor * lightDiffuse * clamp(dot(-normalView, lightDirectionView), 0, 1);
	    finalColor *= visibility;
	} else if(materialType == UNLIT) {
	    finalColor = color;
	} else {
		finalColor = clamp(cookTorrance(lightDirectionView, lightDiffuse, 1.0f, V, positionView, normalView, roughness, metallic, diffuseColor, specularColor), 0, 1);
    	finalColor *= visibility;
	}

	out_DiffuseSpecular.rgb = 4 * finalColor; // TODO: Extract value 4 as global HDR scaler

	float ambient = normalAmbient.a;
	ambient += 0.1;  // Boost ambient here
	out_DiffuseSpecular.rgb += ambient * color.rgb;
	out_DiffuseSpecular.a = 1;


//	mat4 shadowMatrix = directionalLight.viewProjectionMatrix;
//	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
//	positionShadow.xyz /= positionShadow.w;
//	positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
//	float depthInLightSpace = positionShadow.z;
//	vec2 shadowMapCoords = positionShadow.xy;
//	sampler2D directionalLightShadowMap = sampler2D(directionalLight.shadowMapHandle);
//	float shadowDepth = textureLod(directionalLightShadowMap, shadowMapCoords, 0).r;
//	float bias = 0.005;
//	if((depthInLightSpace - bias) < shadowDepth) {
//		out_DiffuseSpecular.rgb = vec3(1);
//	} else {
//		out_DiffuseSpecular.rgb = vec3(0);
//	}

//	out_DiffuseSpecular = vec4(st, 0f,1f);
//	out_DiffuseSpecular = vec4(lightDirectionView,1);
	//out_DiffuseSpecular.rgb = vec3(depthInLightSpace,depthInLightSpace,depthInLightSpace);
	//out_DiffuseSpecular.rgb = vec3(positionShadow.xyz);
}
