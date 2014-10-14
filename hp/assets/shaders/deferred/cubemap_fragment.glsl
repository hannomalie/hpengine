#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=6) uniform sampler2D shadowMap;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;
uniform vec3 materialDiffuseColor = vec3(0,0,0);

uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform vec3 lightAmbient;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec4 position_clip;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;

layout(location=0)out vec4 out_color;

float linearizeDepth(float z)
{
  float n = 0.1; // camera z near TODO: MAKE THIS UNIFORRRRRRRMMMM
  float f = 500; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}

float evaluateVisibility(float depthInLightSpace, vec4 ShadowCoordPostW) {
	
	vec4 shadowMapSample = texture2D(shadowMap,ShadowCoordPostW.xy);
	const float bias = 0.001;
	if (depthInLightSpace < shadowMapSample.x + bias) {
		return vec3(1.0,1.0,1.0);
	}

	return vec3(0.1,0.1,0.1);
}
void main()
{
	vec2 UV = texCoord;
	vec4 color = vec4(materialDiffuseColor, 1);
	color = texture2D(diffuseMap, UV);
	
	float depth = (position_clip.z / position_clip.w);
    out_color = vec4(color.rgb, depth);
    
	vec3 PN_world = normalize(normal_world);
	
	/////////////////// SHADOWMAP
	float visibility = 1.0;
	vec4 positionShadow = (shadowMatrix * vec4(position_world.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
	visibility = clamp(evaluateVisibility(depthInLightSpace, positionShadow), 0, 1);
	/////////////////// SHADOWMAP
	
	out_color.rgb = color.rgb;// * lightAmbient;
	out_color.rgb += color.rgb * lightDiffuse * max(dot(-lightDirection, PN_world), 0) * visibility;
	//out_color.rgb = vec3(visibility,visibility,visibility);
}