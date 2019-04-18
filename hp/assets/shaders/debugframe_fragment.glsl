
uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

float linearizeDepth(in float depth)
{
    float zNear = 0.1;
    float zFar  = 2000;
    return (2.0 * zNear) / (zFar + zNear - depth * (zFar - zNear));
}

void main()
{
	vec4 in_color = textureLod(renderedTexture, pass_TextureCoord, 0);
//	float temp = 10*linearizeDepth(in_color.r);
//    float finalTemp = temp;
//	in_color = vec4(in_color.r+finalTemp,in_color.g+finalTemp,in_color.b+finalTemp,1);
//	in_color = vec4(temp,temp,temp,1);
//    in_color = vec4(in_color.a*10);

    out_color = vec4(in_color.rgb,1);
}