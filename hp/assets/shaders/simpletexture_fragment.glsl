
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

float linearizeDepth(in float depth)
{
    float zNear = 1;
    float zFar  = 1000;
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

void main()
{
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 0);
	//float temp = 0.5*(in_color.r);
	//temp *= temp;
	//float temp = 50*(in_color.g);
	float temp = linearizeDepth(in_color.a);
//	temp = in_color.g;
//	in_color = vec4(temp,temp,temp,1);

    out_color = vec4(in_color.rgb,1);
}