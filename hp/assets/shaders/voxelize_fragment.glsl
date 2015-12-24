
#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

flat in int f_axis;   //indicate which axis the projection uses
flat in vec4 f_AABB;

in vec3 f_normal;
in vec2 f_texcoord;
in vec3 f_pos;

layout (location = 0) out vec4 gl_FragColor;
layout (pixel_center_integer) in vec4 gl_FragCoord;

uniform layout(binding = 0, rgba16f) image3D out_voxel;

//include(globals_structs.glsl)
uniform int materialIndex = 0;
layout(std430, binding=3) buffer _materials {
	Material materials[2000];
};

uniform int u_width;
uniform int u_height;

void main()
{
//    if( f_pos.x < f_AABB.x || f_pos.y < f_AABB.y || f_pos.x > f_AABB.z || f_pos.y > f_AABB.w )
//	   discard ;

	Material material = materials[materialIndex];
	vec3 materialDiffuseColor = vec3(material.diffuseR,
									 material.diffuseG,
									 material.diffuseB);

	if(material.hasDiffuseMap != 0) {
        materialDiffuseColor = texture(sampler2D(uint64_t(material.handleDiffuse)), f_texcoord).rgb;
    } else {
        materialDiffuseColor = vec3(0.0,1.0,0.0);
    }

    vec4 data = vec4(0.0,0.1,0.0,0.0);
    data.rgb = materialDiffuseColor;
    //ivec3 temp = ivec3( gl_FragCoord.x, gl_FragCoord.y, u_width * gl_FragCoord.z ) ;
	uvec4 temp = uvec4( gl_FragCoord.x, gl_FragCoord.y, u_width * gl_FragCoord.z, 0 ) ;
	uvec4 texcoord;
	if( f_axis == 1 )
	{
	    texcoord.x = u_width - temp.z;
		texcoord.z = temp.x;
		texcoord.y = temp.y;
	}
	else if( f_axis == 2 )
    {
	    texcoord.z = temp.y;
		texcoord.y = u_width-temp.z;
		texcoord.x = temp.x;
	}
	else
	    texcoord = temp;


	imageStore( out_voxel, ivec3(0), data );
//	imageStore( out_voxel, ivec3(texcoord), data );
//	imageStore( out_voxel, ivec3(gl_FragCoord), data );
}