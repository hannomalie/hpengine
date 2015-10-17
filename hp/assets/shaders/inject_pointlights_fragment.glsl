layout(binding=0) uniform sampler2D shadowMap;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

uniform float scaleX = 1;
uniform float scaleY = 1;
uniform int mipmap = 0;

in vec2 pass_TextureCoord;

//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _lights {
	float pointLightCount;
	PointLight pointLights[1000];
};

layout(location=0)out vec4 diffuseSpecular;
layout(location=1)out vec4 out_AOReflection;

void main(void) {

	vec2 st = pass_TextureCoord * vec2(scaleX, scaleY);
	ivec2 coords = ivec2(st * vec2(screenWidth, screenHeight));


	const int lightsPerDimension = 40;

	float factor = 2048f / lightsPerDimension;
	vec4 depthPositionWorld = texture(shadowMap, st*10);
	vec3 positionWorld = depthPositionWorld.gba;

//	if(coords.y < lightsPerDimension && coords.x < lightsPerDimension) {

		const int INJECTED_POINTLIGHTS_START_INDEX = 500;
		uint index = INJECTED_POINTLIGHTS_START_INDEX + coords.x;
		pointLights[index].positionX = positionWorld.x;
		pointLights[index].positionY = positionWorld.y;
		pointLights[index].positionZ = positionWorld.z;

		pointLights[index].radius = 20;
		pointLights[index].colorR = 1;
		pointLights[index].colorG = 0;
		pointLights[index].colorB = 0;
//	}

	diffuseSpecular = vec4(0,0,0,0);
//	diffuseSpecular.rgba = vec4(positionWorld, 1);
}
