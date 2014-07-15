#version 420

out vec4 out_Color;
//out float out_Color;
in vec4 pass_Position;

void main()
{
	float depth = gl_FragCoord.z;
	
	float moment1 = depth;
	float moment2 = depth * depth;
	
	float dx = dFdx(depth);
	float dy = dFdy(depth);
	moment2 += 0.25*(dx*dx+dy*dy) ;
		
    out_Color = vec4(moment1,moment2,0,1);
}