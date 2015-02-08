
out vec4 out_Color;
in vec4 pass_Position;
in vec3 normal_world;

void main()
{
	float depth = gl_FragCoord.z;
		
    out_Color = vec4(depth,normal_world);
}