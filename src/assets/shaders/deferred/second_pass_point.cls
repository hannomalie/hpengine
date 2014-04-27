__kernel void main(__read_only image2d_t albedo,
				   //__read_only image2d_t normals,
				   //__read_only image2d_t positions,
				   __write_only image2d_t result)
{
	int2 coords = (int2)(get_global_id(0), get_global_id(1));
	float4 out_color = (float4)(1.0f, 0.0f, 0.0f, 1.0f);
	write_imagef(result, coords, out_color);
}