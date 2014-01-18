#version 420

uniform sampler2D renderedTexture;

out vec4 out_color;

void main()
{
    vec2 texCoords = vec2(0,0);
    texCoords.x = (gl_FragCoord.x / 800);
    texCoords.y = (gl_FragCoord.y / 600);
    vec4 in_color = texture2D(renderedTexture, texCoords);
    in_color.r = 1;
    out_color = in_color;
}