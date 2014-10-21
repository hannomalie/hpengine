#version 420

//out float out_Color;

uniform float near = 0.1;
uniform float far = 100.0;

in vec4 pass_Position;
in vec4 pass_WorldPosition;
in vec3 normal_world;

out vec4 out_Color;
out vec4 out_Normal;
out vec4 out_Position;

float linearizeDepth(float z)
{
  float n = near; // camera z near
  float f = far; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
}
float packColor(vec3 color) {
    return color.r + color.g * 256.0 + color.b * 256.0 * 256.0;
}

vec3 unpackColor(float f) {
    vec3 color;
    color.b = floor(f / 256.0 / 256.0);
    color.g = floor((f - color.b * 256.0 * 256.0) / 256.0);
    color.r = floor(f - color.b * 256.0 * 256.0 - color.g * 256.0);
    // now we have a vec3 with the 3 components in range [0..256]. Let's normalize it!
    return color / 256.0;
}

#define kPI 3.1415926536f
vec2 encode(vec3 n) {
	//n = vec3(n*0.5+0.5);
    return (vec2((atan(n.x, n.y)/kPI), n.z)+vec2(1,1))*0.5;
}

void main()
{
	float depth = pass_Position.z/pass_Position.w;//(gl_FragCoord.z);
	
	float moment1 = depth;
	float moment2 = depth * depth;
	
	float dx = dFdx(depth);
	float dy = dFdy(depth);
	moment2 += 0.25*(dx*dx+dy*dy) ;
    out_Color = vec4(moment1,moment2,packColor(normal_world),1);
    out_Normal = vec4(normal_world,1);
    out_Position = vec4(pass_WorldPosition.xyz,1);
    //out_Color = vec4(moment1,moment2,encode(normal_world));
}