
uniform sampler2D renderedTexture;
uniform vec3 diffuseColor = vec3(1,0,0);

in vec2 pass_TextureCoord;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility

void main()
{
	//vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 1);
    
    out_color = vec4(14*diffuseColor,0.);
    out_normal.a = 1;
}
