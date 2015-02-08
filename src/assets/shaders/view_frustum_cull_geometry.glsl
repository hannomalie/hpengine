layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;

uniform bool frustumCulling;
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform vec4 FrustumPlanes[6];


in vec4 out_color;
in vec2 out_texCoord;
in vec3 out_normalVec;
in vec3 out_normal_model;
in vec3 out_normal_world;
in vec4 out_position_clip;
in vec4 out_position_clip_uv;
in vec4 out_position_clip_shadow;
in vec4 out_position_world;
in vec3 out_view_up;
in vec3 out_view_back;
in vec3 out_lightVec;
in vec3 out_halfVec;
in vec3 out_eyeVec;

out vec4 color;
out vec2 texCoord;
out vec3 normalVec;
out vec3 normal_model;
out vec3 normal_world;
out vec4 position_clip;
out vec4 position_clip_uv;
out vec4 position_clip_shadow;
out vec4 position_world;
out vec3 view_up;
out vec3 view_back;
out vec3 lightVec;
out vec3 halfVec;
out vec3 eyeVec;


bool PointInFrustum(in vec3 p) {
  for(int i=0; i < 6; i++)
  {
    vec4 plane=FrustumPlanes[i];
    if ((plane.x * p.x + plane.y * p.y + plane.z * p.z) <= 0) {
    	return false;
    }
  }
  return true;
}
void setOutputForFragmentShader() {
	color = out_color;
	texCoord = out_texCoord;
	normalVec = out_normalVec;
	normal_model = out_normal_model;
	normal_world = out_normal_world;
	position_clip = out_position_clip;
	position_clip_uv = out_position_clip_uv;
	position_clip_shadow = out_position_clip_shadow;
	position_world = out_position_world;
	view_up = out_view_up;
	view_back = out_view_back;
	lightVec = out_lightVec;
	halfVec = out_halfVec;
	eyeVec = out_eyeVec;
}
void main()
{
  for(int i=0;i<gl_in.length(); i++) {
    vec4 vInPos = gl_in[i].gl_Position;
    vec2 tmp = (vInPos.xz*2-1.0)*5;
    vec3 V = vec3(tmp.x, vInPos.y, tmp.y);
    gl_Position = vInPos;
    if(!frustumCulling || PointInFrustum(V)) {
      EmitVertex();
    }
  }
  EndPrimitive();
  setOutputForFragmentShader();
  
}