layout ( triangles ) in;
layout ( triangle_strip, max_vertices = 3 ) out;

in vec3 v_lightmapTextureCoord[3];
in vec4 v_positionWorld[3];

out vec3 lightmapTextureCoord;
out vec4 positionWorld;

uniform int layer;

void main()
{
	for(int i = 0; i < gl_in.length(); i++) {
        gl_Position = gl_in[i].gl_Position;
        gl_Layer = layer;
        lightmapTextureCoord = v_lightmapTextureCoord[i].xyz;
        positionWorld = v_positionWorld[i].xyzw;
        EmitVertex();
	}

	EndPrimitive();
}
