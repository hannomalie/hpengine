
#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

flat in int f_axis;   //indicate which axis the projection uses
flat in vec4 f_AABB;

in vec3 f_normal;
in vec3 f_pos;
in vec2 f_texcoord;

//layout (pixel_center_integer) in vec4 gl_FragCoord;

uniform layout(binding = 5, rgba8) image3D out_voxel;

//include(globals_structs.glsl)
uniform int materialIndex;
uniform int entityIndex;
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
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

    materialDiffuseColor *= float(material.ambient);

    vec4 data = vec4(0.0,1,0.0,0.0);
    data.rgb = materialDiffuseColor;


    imageStore(out_voxel, ivec3(f_pos.xyz + ivec3(128)), vec4(data.rgba));
}