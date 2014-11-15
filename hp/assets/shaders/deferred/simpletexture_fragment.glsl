#version 420

uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 5000;
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

void main()
{
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 1);
	//in_color.r = pow(in_color.r, 1);
	float temp = 10*(in_color.g);
	//in_color = vec4(temp,temp,temp,1);
    
    out_color = vec4(in_color.rgb,1);
}