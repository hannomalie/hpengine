#version 420

out vec4 out_Color;
in vec4 pass_Position;

void main()
{
	float depth = gl_FragCoord.z;
		
    out_Color = vec4(depth,depth,depth,depth);
}