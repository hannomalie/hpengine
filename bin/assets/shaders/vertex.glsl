#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform mat4 projectionMatrixShadow;
uniform mat4 viewMatrixShadow;

uniform vec3 eyePosition;
uniform vec3 lightDirection;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Binormal;
in vec3 in_Tangent;

out vec4 pass_Color;
out vec2 pass_TextureCoord;
out vec3 pass_Normal;
out vec4 pass_Position;
out vec4 pass_PositionWorld;
out vec4 pass_PositionShadow;
out vec3 pass_Binormal;
out vec3 pass_Tangent;

out vec3 pass_Up;
out vec3 pass_Back;

out vec3 pass_LightDirection;
out vec3 pass_LightVec;
out vec3 pass_HalfVec;
out vec3 pass_eyeVec;

mat4 biasMatrix = mat4(
	0.5, 0.0, 0.0, 0.0,
	0.0, 0.5, 0.0, 0.0,
	0.0, 0.0, 0.5, 0.0,
	0.5, 0.5, 0.5, 1.0
);

void main(void) {

	pass_PositionWorld = viewMatrix * modelMatrix * vec4(in_Position.xyz,1);
	pass_Position = (projectionMatrix * pass_PositionWorld);
	gl_Position = pass_Position;
	pass_PositionShadow = projectionMatrixShadow * viewMatrixShadow * modelMatrix * vec4(in_Position.xyz,1);
	pass_PositionShadow.xyz /= pass_PositionShadow.w;
	pass_PositionShadow.xyz += 1.0;
	pass_PositionShadow.xyz *= 0.5;
	//gl_Position = pass_PositionShadow;
	
	pass_Color = in_Color;
	pass_TextureCoord = in_TextureCoord;
	pass_Normal = (viewMatrixShadow * modelMatrix * vec4(in_Normal,1)).xyz;
	
	pass_Up = vec3(viewMatrix[1][0], viewMatrix[1][1], viewMatrix[1][2]);
	pass_Back = vec3(viewMatrix[2][0], viewMatrix[2][1], viewMatrix[2][2]);
	
	//vec3 lightDir = normalize(lightPosition - gl_Position.xyz);
	vec3 lightDir = normalize(lightDirection);
	pass_LightDirection = lightDir;
	pass_LightVec = lightDir;
	
	pass_eyeVec = (viewMatrix * modelMatrix * vec4(eyePosition,1)).xyz - pass_PositionWorld.xyz;
	
	vec3 halfVector = normalize(pass_PositionWorld.xyz + lightDir);
	pass_HalfVec = halfVector; 
}