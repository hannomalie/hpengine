
uniform mat4 modelMatrix;

uniform vec3 pointLightPositionWorld;
uniform float pointLightRadius;

uniform bool isBack;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;

out vec4 vs_pass_WorldPosition;
out vec4 pass_ProjectedPosition;
out vec2 vs_pass_texCoord;
out float clip;

void main()
{
	vs_pass_WorldPosition = modelMatrix * vec4(in_Position.xyz,1);

    //gl_Position = pass_WorldPosition;

	vs_pass_texCoord = in_TextureCoord;
	vs_pass_texCoord.y = 1 - in_TextureCoord.y;
}