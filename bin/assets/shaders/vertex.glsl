#version 420

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightPosition;

in vec4 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Binormal;
in vec3 in_Tangent;

out vec4 pass_Color;
out vec2 pass_TextureCoord;
out vec3 pass_Normal;
out vec3 pass_Position;
out vec3 pass_Binormal;
out vec3 pass_Tangent;

out vec3 pass_Up;
out vec3 pass_Back;

out vec3 pass_LightDirection;
out vec3 pass_LightVec;
out vec3 pass_HalfVec;
out vec3 pass_eyeVec;

void main(void) {

	gl_Position = projectionMatrix * viewMatrix * modelMatrix * in_Position;
	
	pass_Color = in_Color;
	pass_TextureCoord = in_TextureCoord;
	pass_Normal =  in_Normal;
	pass_Position =  in_Position.xyz;
	
	pass_Up = vec3(viewMatrix[1][0], viewMatrix[1][1], viewMatrix[1][2]);
	pass_Back = vec3(viewMatrix[2][0], viewMatrix[2][1], viewMatrix[2][2]);
	
	vec3 lightDir = normalize(lightPosition - gl_Position.xyz);
	pass_LightDirection = lightDir;
	vec3 v;
	v.x = dot(lightDir, in_Tangent);
	v.y = dot(lightDir, in_Binormal);
	v.z = dot(lightDir, in_Normal);
	pass_LightVec = normalize(v);
	
	v.x = dot(in_Position.xyz, in_Tangent);
	v.y = dot(in_Position.xyz, in_Binormal);
	v.z = dot(in_Position.xyz, in_Normal);
	pass_eyeVec = normalize(v);
	
	vec3 halfVector = normalize(in_Position.xyz + lightDir);
	v.x = dot (halfVector, in_Tangent);
	v.y = dot (halfVector, in_Binormal);
	v.z = dot (halfVector, in_Normal);
	pass_HalfVec = v; 
}