#version 420

uniform sampler2D renderedTexture;

in vec2 pass_TextureCoord;

out vec4 out_color;

void main()
{
	//vec4 in_color = texture2D(renderedTexture, vec2((gl_FragCoord.x / 400), (gl_FragCoord.y / 300)));
	vec4 in_color = texture2D(renderedTexture, pass_TextureCoord);

    //if (gl_FragCoord.x < 400 && gl_FragCoord.y < 300) {
    //	//in_color = texture2D(renderedTexture, pass_TextureCoord*2);
    //	in_color.r = 1;
    //}
    
    out_color = in_color;
}