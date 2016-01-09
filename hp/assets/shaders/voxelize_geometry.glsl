layout ( triangles ) in;
layout ( triangle_strip, max_vertices = 3 ) out;

in vec3 v_vertex[];
in vec3 v_normal[];
in vec2 v_texcoord[];

in vec4 position_world[];

out vec3 g_normal;
out vec3 g_pos;
out vec2 g_texcoord;

flat out int f_axis;   //indicate which axis the projection uses
flat out vec4 f_AABB;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform int entityIndex;

uniform mat4 u_MVPx;
uniform mat4 u_MVPy;
uniform mat4 u_MVPz;
uniform int u_width;
uniform int u_height;

void main()
{
	vec3 faceNormal = normalize( cross( v_vertex[1]-v_vertex[0], v_vertex[2]-v_vertex[0] ) );
	float NdotXAxis = abs( faceNormal.x );
	float NdotYAxis = abs( faceNormal.y );
	float NdotZAxis = abs( faceNormal.z );
	mat4 proj;

	for(int i = 0; i<gl_in.length(); i++) {
	vec3 n = faceNormal;
	    float maxC = max(abs(n.x), max(abs(n.y), abs(n.z)));
            float x,y,z;
            x = abs(n.x) < maxC ? 0 : 1;
            y = abs(n.y) < maxC ? 0 : 1;
            z = abs(n.z) < maxC ? 0 : 1;

            vec4 axis = vec4(x,y,z,1);

            if(axis == vec4(1,0,0,1)){
                proj=u_MVPx;
            }
            else if(axis == vec4(0,1,0,1)){
                proj=u_MVPy;
            }
            else if(axis == vec4(0,0,1,1)){
                proj=u_MVPz;
            }

            proj = projectionMatrix;

            gl_Position = proj * vec4(v_vertex[i],1);
            g_pos = gl_in[i].gl_Position.xyz;
            g_normal = v_normal[i];
            g_texcoord = v_texcoord[i];
            EmitVertex();
	}

	EndPrimitive();
}