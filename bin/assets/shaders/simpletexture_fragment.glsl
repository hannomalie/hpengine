#version 420

uniform sampler2D tex;
uniform vec2 tex_size;

layout(location = 0) out vec4 out_color;

void main()
{
    vec4 in_color = texture2D(tex, gl_FragCoord.xy / tex_size);
    in_color = vec4(0,0,0,1);
    out_color = in_color;
}