
uniform mat4 modelMatrix;

uniform vec3 pointLightPositionWorld;
uniform float pointLightRadius;

uniform bool isBack;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

out vec4 pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out vec2 texCoord;
out float clip;

void main()
{
	pass_WorldPosition = modelMatrix * vec4(in_Position.xyz,1);

	pass_ProjectedPosition.xyz = pass_WorldPosition.xyz - pointLightPositionWorld;
	if(isBack) { pass_ProjectedPosition.z = -pass_ProjectedPosition.z; }

	float L = length(pass_ProjectedPosition.xyz);
	pass_ProjectedPosition /= L;
	clip = pass_ProjectedPosition.z;
	pass_ProjectedPosition.z = pass_ProjectedPosition.z + 1;
	pass_ProjectedPosition.x = pass_ProjectedPosition.x / pass_ProjectedPosition.z;
	pass_ProjectedPosition.y = pass_ProjectedPosition.y / pass_ProjectedPosition.z;
	const float NearPlane = 0.0001;
	float FarPlane = pointLightRadius;
	pass_ProjectedPosition.z = (L - NearPlane) / (FarPlane - NearPlane);
    pass_ProjectedPosition.w = 1;

    gl_Position = pass_ProjectedPosition;

	texCoord = in_TextureCoord;
	texCoord.y = 1 - in_TextureCoord.y;
}