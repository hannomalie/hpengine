layout(location=0)out vec4 out_color;

in vec4 position_clip;

void main() {
	float depth = (position_clip.z / position_clip.w);
  	out_color.r = depth;
}