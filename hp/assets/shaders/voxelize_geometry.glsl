layout ( triangles ) in;
layout ( triangle_strip, max_vertices = 3 ) out;

in vec3 v_vertex[3];
in vec3 v_normal[3];
in vec2 v_texcoord[3];

in vec4 position_world[3];

out vec3 g_normal;
out vec3 g_pos;
out vec2 g_texcoord;

flat out int g_axis;   //indicate which axis the projection uses
flat out vec4 g_AABB;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform int entityIndex;

uniform mat4 u_MVPx;
uniform mat4 u_MVPy;
uniform mat4 u_MVPz;
uniform int u_width;
uniform int u_height;

uniform float sceneScale = 1f;
uniform float inverseSceneScale = 1f;
uniform int gridSize = 256;

uniform vec3 lightDirection;
uniform vec3 lightColor;

void main()
{
	vec3 faceNormal = normalize( cross( v_vertex[1]-v_vertex[0], v_vertex[2]-v_vertex[0] ) );
	float NdotXAxis = abs( faceNormal.x );
	float NdotYAxis = abs( faceNormal.y );
	float NdotZAxis = abs( faceNormal.z );
	mat4 proj;

    vec4 AABB;
    vec2 hPixel = vec2( sceneScale, sceneScale );
    float pl = 1.4142135637309 / float(gridSize);
    //calculate AABB of this triangle
    AABB.xy = v_vertex[0].xy;
    AABB.zw = v_vertex[0].xy;
    AABB.xy = min( v_vertex[1].xy, AABB.xy );
    AABB.zw = max( v_vertex[1].xy, AABB.zw );
    AABB.xy = min( v_vertex[2].xy, AABB.xy );
    AABB.zw = max( v_vertex[2].xy, AABB.zw );
    //Enlarge half-pixel
    AABB.xy -= hPixel;
    AABB.zw += hPixel;

    //find 3 triangle edge plane
    vec3 e0 = vec3( v_vertex[1].xy - v_vertex[0].xy, 0 );
    vec3 e1 = vec3( v_vertex[2].xy - v_vertex[1].xy, 0 );
    vec3 e2 = vec3( v_vertex[0].xy - v_vertex[2].xy, 0 );
    vec3 n0 = cross( e0, vec3(0,0,1) );
    vec3 n1 = cross( e1, vec3(0,0,1) );
    vec3 n2 = cross( e2, vec3(0,0,1) );

    //dilate the triangle
    vec3[3] cr_pos;
    cr_pos[0].xy = v_vertex[0].xy + pl*( (e2.xy/dot(e2.xy,n0.xy)) + (e0.xy/dot(e0.xy,n2.xy)) );
    cr_pos[1].xy = v_vertex[1].xy + pl*( (e0.xy/dot(e0.xy,n1.xy)) + (e1.xy/dot(e1.xy,n0.xy)) );
    cr_pos[2].xy = v_vertex[2].xy + pl*( (e1.xy/dot(e1.xy,n2.xy)) + (e2.xy/dot(e2.xy,n1.xy)) );

	for(int i = 0; i < gl_in.length(); i++) {
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

        vec3 vertexTemp = v_vertex[i];
//        conservative rasterization
//        vertexTemp = cr_pos[i].xyz;


        gl_Position = proj * vec4(vertexTemp,1);
        g_pos = vertexTemp.xyz;

        g_normal = v_normal[i];
        g_texcoord = v_texcoord[i];
        EmitVertex();
	}

	EndPrimitive();
}
