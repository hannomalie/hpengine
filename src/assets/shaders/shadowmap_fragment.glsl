#version 420

//out vec4 out_Color;
out float out_Color;
in vec4 pass_Position;

void main()
{
    //out_Color = vec4(gl_FragCoord.z,gl_FragCoord.z,gl_FragCoord.z,gl_FragCoord.z);
    out_Color = gl_FragCoord.z;
   //out_Color = log(gl_FragCoord.w + 1.0);
    //out_Color = vec4(0.5, 0.5, 0.5, 1);
}