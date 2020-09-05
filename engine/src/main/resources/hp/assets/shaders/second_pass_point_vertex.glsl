
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightPosition;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec4 in_Position;

out vec4 position_clip;
out vec4 position_view;
out vec4 position_world;

void main(void) {
	gl_Position = projectionMatrix * viewMatrix * modelMatrix * (in_Position );
	position_world = modelMatrix * (in_Position );
	position_view = viewMatrix * position_world;
}