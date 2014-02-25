#version 420

out vec4 out_Color;
in vec4 pass_Position;

void main()
{
    //out_Color = vec4(gl_FragCoord.x,gl_FragCoord.y,gl_FragCoord.z,gl_FragCoord.w);
    out_Color = vec4(gl_FragCoord.z,gl_FragCoord.z,gl_FragCoord.z,gl_FragCoord.z);
    //out_Color = vec4(0.5, 0.5, 0.5, 1);
}