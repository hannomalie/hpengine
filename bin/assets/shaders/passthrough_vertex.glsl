#version 420

in vec4 in_position;

out vec2 texcoords;

void main()
{
    gl_Position = in_position;
    texcoords = in_position.xy * vec2(0.5, 0.5) + vec2(0.5, 0.5);
}