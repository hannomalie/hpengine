
uniform vec3 diffuseColor = vec3(1,1,0);

layout(location=0)out vec4 out_color;

void main()
{
    out_color = vec4(diffuseColor,1);
}
