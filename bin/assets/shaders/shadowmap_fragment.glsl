#version 420

//out float fragmentdepth;
out vec4 out_Color;

void main()
{
    out_Color = vec4(gl_FragDepth,gl_FragDepth,gl_FragDepth,gl_FragDepth);
    //out_Color = vec4(0.5, 0.5, 0.5, 1);
}