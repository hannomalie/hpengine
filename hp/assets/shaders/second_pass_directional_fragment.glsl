
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=7) uniform sampler2D visibilityMap;

layout(binding=8) uniform samplerCubeArray probes;

#ifdef BINDLESSTEXTURES
#else
layout(binding=8) uniform sampler2D directionalLightShadowMap;
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

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

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

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	float depth = texture2D(normalMap, st).w;
	vec3 positionView = texture2D(positionMap, st).xyz;
	//vec4 positionViewPreW = (inverse(projectionMatrix)*vec4(st, depth, 1));
	//positionView = positionViewPreW.xyz / positionViewPreW.w;
  	
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	vec3 color = texture2D(diffuseMap, st).xyz;
	float roughness = texture2D(positionMap, st).a;
	
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
	vec4 normalAmbient = texture2D(normalMap, st);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView,0.0)).xyz;
	
	float metallic = texture2D(diffuseMap, st).a;
	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, roughness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

	vec3 lightDirectionView = (viewMatrix * vec4(-directionalLight.direction, 0)).xyz;
	vec3 finalColor;

	int materialIndex = int(textureLod(visibilityMap, st, 0).b);
	Material material = materials[materialIndex];
	int materialType = int(material.materialtype);
	int DEFAULT = 0;
	int FOLIAGE = 1;
	int UNLIT = 2;
	vec3 lightDiffuse = directionalLight.color;

	#if BINDLESS_TEXTURES
	float visibility = getVisibility(positionWorld, directionalLight);
	#else
	float visibility = getVisibility(positionWorld.xyz, directionalLight, directionalLightShadowMap);

	#endif
	if(materialType == FOLIAGE) {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse,
									1, V, positionView, normalView,
									roughness, 0, diffuseColor, specularColor);
		finalColor += diffuseColor * lightDiffuse * clamp(dot(-normalView, lightDirectionView), 0, 1);
	    finalColor *= visibility;
	} else if(materialType == UNLIT) {
	    finalColor = color;
	} else {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse, 1.0f, V, positionView, normalView, roughness, metallic, diffuseColor, specularColor);
    	finalColor *= visibility;
	}


	out_DiffuseSpecular.rgb = 4 * finalColor; // TODO: Extract value 4 as global HDR scaler

	float ambient = normalAmbient.a;
	ambient += 0.1;  // Boost ambient here
//	out_DiffuseSpecular.rgb += ambient * color.rgb;
	out_DiffuseSpecular.a = 1;

//	out_DiffuseSpecular = vec4(1,0.1,0, 1);

//	out_DiffuseSpecular = vec4(color,1);
	//out_DiffuseSpecular.rgb = vec3(depthInLightSpace,depthInLightSpace,depthInLightSpace);
	//out_DiffuseSpecular.rgb = vec3(positionShadow.xyz);
}
