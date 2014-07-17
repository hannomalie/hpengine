#version 420

out vec4 out_Color;
//out float out_Color;
in vec4 pass_Position;

uniform float near = 0.1;
uniform float far = 100.0;

float linearizeDepth(float z)
{
  float n = near; // camera z near
  float f = far; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}
void main()
{
	float depth = pass_Position.z/pass_Position.w;//(gl_FragCoord.z);
	
	float moment1 = depth;
	float moment2 = depth * depth;
	
	float dx = dFdx(depth);
	float dy = dFdy(depth);
	moment2 += 0.25*(dx*dx+dy*dy) ;
		
    out_Color = vec4(moment1,moment2,0,1);
}