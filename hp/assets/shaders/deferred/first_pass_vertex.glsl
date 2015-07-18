
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 lastViewMatrix;
uniform mat4 modelMatrix;
uniform mat4 lightMatrix;

//uniform mat4 projectionMatrixShadow;
//uniform mat4 viewMatrixShadow;

uniform vec3 eyePosition;
uniform int time = 0;
//uniform vec3 lightPosition;

uniform bool isInstanced = false;

in vec3 in_Position;
in vec4 in_Color;
in vec2 in_TextureCoord;
in vec3 in_Normal;
in vec3 in_Tangent;
in vec3 in_Binormal;

out vec4 color;
out vec2 texCoord;
out vec3 normalVec;
out vec3 normal_model;
out vec3 normal_world;
out vec3 normal_view;
out vec3 tangent_world;
out vec3 bitangent_world;
out vec4 position_clip;
out vec4 position_clip_last;
out vec4 position_clip_uv;
out vec4 position_world;
//out vec4 position_clip_shadow;
//out vec3 view_up;
//out vec3 view_back;
out vec3 lightVec;
out vec3 halfVec;
out vec3 eyeVec;
out vec3 eyePos_world;
out mat3 TBN;

void main(void) {

	position_world = modelMatrix * vec4(in_Position.xyz,1);

//	if(isInstanced) {
//		position_world.x += float(gl_InstanceID/50) * 30.0f;
//		position_world.z += float(gl_InstanceID%50) * 25.0f;
//	}

	position_clip = (projectionMatrix * viewMatrix * position_world);
	position_clip_last = (projectionMatrix * lastViewMatrix * position_world);
	gl_Position = position_clip;
	//position_clip_shadow = projectionMatrixShadow * viewMatrixShadow * modelMatrix * vec4(in_Position.xyz,1);
	//position_clip_shadow.xyz /= position_clip_shadow.w;
	//position_clip_shadow.xyz += 1.0;
	//position_clip_shadow.xyz *= 0.5;
	
	position_clip_uv.xyz = position_clip.xyz;
	position_clip_uv /= position_clip_uv.w;
	position_clip_uv.xyz += 1;
	position_clip_uv.xyz *= 0.5;
	
	color = in_Color;
	texCoord = in_TextureCoord;
	texCoord.y = 1 - in_TextureCoord.y;
	
	normalVec = in_Normal;
	normal_model = (vec4(in_Normal,0)).xyz;
	normal_world.x = dot(modelMatrix[0].xyz, normal_model);
    normal_world.y = dot(modelMatrix[1].xyz, normal_model);
    normal_world.z = dot(modelMatrix[2].xyz, normal_model);
	//normal_world = (inverse(transpose(modelMatrix)) * vec4(normal_model,0)).xyz;
	normal_view = (viewMatrix * vec4(normal_world,0)).xyz;
	
	/*vec3 tangent_model = in_Tangent;
	tangent_world.x = dot(modelMatrix[0].xyz, tangent_model);
    tangent_world.y = dot(modelMatrix[1].xyz, tangent_model);
    tangent_world.z = dot(modelMatrix[2].xyz, tangent_model);
	vec3 bitangent_model = in_Binormal;
	bitangent_world.x = dot(modelMatrix[0].xyz, bitangent_model);
    bitangent_world.y = dot(modelMatrix[1].xyz, bitangent_model);
    bitangent_world.z = dot(modelMatrix[2].xyz, bitangent_model);*/
    
	
	eyePos_world = ( vec4(eyePosition,1)).xyz;
	eyeVec = (position_world.xyz - eyePos_world);
	
}