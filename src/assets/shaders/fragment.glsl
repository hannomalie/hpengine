#version 420

layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform vec3 ld;

in vec4 pass_Color;
in vec2 pass_TextureCoord;
in vec3 pass_Normal;
in vec3 pass_Position;
in vec3 pass_Up;
in vec3 pass_Back;

out vec4 out_Color;

void main(void) {
	vec4 diffuseMaterial = texture2D(diffuseMap, pass_TextureCoord);
	vec4 specularMaterial = vec4(1.0, 0.3, 0.6, 1);
	vec3 lightDirection = ld;
	vec4 diffuseLight = vec4(1,1,1,1);
	vec4 specularLight = vec4(1,1,1,1);

	vec3 normal = pass_Normal;
	normal = 2.0 * texture2D(normalMap, pass_TextureCoord).rgb - 1.0;
	normal = (vec4(normal, 1) * viewMatrix * modelMatrix).xyz;
	normal = normalize (normal);
	
	
	
	float lamberFactor = max(dot(normalize(lightDirection), normal), 0.0);
	if (lamberFactor > 0.0)
	{
		out_Color = diffuseMaterial;
		out_Color = lamberFactor * out_Color;
	}
}