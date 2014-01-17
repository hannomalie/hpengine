#version 420

uniform sampler2D renderedTexture;
uniform vec2 tex_size;

out vec4 out_color;

void main()
{
    //vec4 in_color = texture2D(renderedTexture, gl_FragCoord.xy / tex_size);
    vec4 in_color = texture2D(renderedTexture, gl_FragCoord.xy);
    in_color.r = 1;
    out_color = in_color;
}