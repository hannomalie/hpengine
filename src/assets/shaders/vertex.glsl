#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 lightMatrix;

uniform mat4 projectionMatrixShadow;
uniform mat4 viewMatrixShadow;

uniform vec3 eyePosition;
uniform vec3 lightPosition;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

out vec4 color;
out vec2 texCoord;
out vec3 normalVec;
out vec3 normal_model;
out vec4 position_clip;
out vec4 position_world;
out vec4 position_clip_shadow;

out vec3 view_up;
out vec3 view_back;

out vec3 lightVec;
out vec3 halfVec;
out vec3 eyeVec;

mat4 biasMatrix = mat4(
	0.5, 0.0, 0.0, 0.0,
	0.0, 0.5, 0.0, 0.0,
	0.0, 0.0, 0.5, 0.0,
	0.5, 0.5, 0.5, 1.0
);

void main(void) {

	position_world = viewMatrix * modelMatrix * vec4(in_Position.xyz,1);
	position_clip = (projectionMatrix * position_world);
	gl_Position = position_clip;
	position_clip_shadow = projectionMatrixShadow * viewMatrixShadow * modelMatrix * vec4(in_Position.xyz,1);
	position_clip_shadow.xyz /= position_clip_shadow.w;
	position_clip_shadow.xyz += 1.0;
	position_clip_shadow.xyz *= 0.5;
	
	color = in_Color;
	texCoord = in_TextureCoord;
	normalVec = in_Normal;
	normal_model = (modelMatrix * vec4(in_Normal,1)).xyz;
	
	view_up = vec3(viewMatrix[1][0], viewMatrix[1][1], viewMatrix[1][2]);
	view_back = vec3(viewMatrix[2][0], viewMatrix[2][1], viewMatrix[2][2]);
	
	vec3 lightVec_world = (lightMatrix * vec4(lightPosition,1)).xyz;
	lightVec = (position_world.xyz - lightVec_world);
	
	vec3 eyePos_world = (vec4(eyePosition,1)).xyz;
	eyeVec = (eyePos_world - position_world.xyz);
	
	halfVec = (position_world.xyz + lightVec_world);
}