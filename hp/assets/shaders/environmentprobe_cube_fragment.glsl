//include(globals_structs.glsl)

#ifdef BINDLESSTEXTURES
#else
layout(binding=0) uniform sampler2D diffuseMap;
uniform bool hasDiffuseMap = false;
layout(binding=1) uniform sampler2D normalMap;
uniform bool hasNormalMap = false;
layout(binding=2) uniform sampler2D specularMap;
uniform bool hasSpecularMap = false;
layout(binding=3) uniform sampler2D occlusionMap;
uniform bool hasOcclusionMap = false;
layout(binding=4) uniform sampler2D heightMap;
uniform bool hasHeightMap = false;
////
layout(binding=7) uniform sampler2D roughnessMap;
uniform bool hasRoughnessMap = false;

#endif
layout(binding=8) uniform samplerCube skyBox;

uniform vec3 pointLightPositionWorld;

in vec4 pass_WorldPosition;
in vec4 pass_ProjectedPosition;
in float clip;
in vec2 pass_texCoord;
flat in Entity pass_Entity;
flat in Material pass_Material;

out vec4 out_Color;
out vec4 out_Diffuse;
out vec4 out_Position;

void main()
{

    if(clip < 0)
		discard;

	Material material = pass_Material;
	vec4 color = vec4(material.diffuse, 1);
	vec2 UV = pass_texCoord;
	float alpha = material.transparency;

#ifdef BINDLESSTEXTURES
	sampler2D diffuseMap;
	bool hasDiffuseMap = uint64_t(material.handleDiffuse) > 0;
	if(hasDiffuseMap) { diffuseMap = sampler2D(material.handleDiffuse); }
#endif

	if(hasDiffuseMap) {
		color = texture(diffuseMap, UV);
		alpha *= color.a;
	}

	float depth = pass_ProjectedPosition.z/pass_ProjectedPosition.w;

    float lightDistance = distance(pass_WorldPosition.xyz, pointLightPositionWorld);
//    lightDistance = lightDistance / 250.0;
    depth = lightDistance;
//    gl_FragDepth = lightDistance;

	float moment1 = (depth);
	float moment2 = moment1 * moment1;
	vec4 result;
	result = vec4(moment1, moment2,0,0);
	out_Color = vec4(color.rgb,1);
	out_Color = vec4(UV,0,1);

//	if(gl_Layer == 0) {
//	    out_Color.r = 1;
//	} else if(gl_Layer == 1) {
//	    out_Color.g = 1;
//	}
//	out_Color = vec4(gl_FragCoord.xy/vec2(512,512),0,0);

//	float dx = dFdx(depth);
//	float dy = dFdy(depth);
	//moment2 += 0.25*(dx*dx+dy*dy);
    //out_Color = vec4(moment1,moment2,packColor(normal_world),1);
    
//    out_Color = vec4(moment1,moment2,0,0);//encode(normal_world));
    //out_Color.rgba = vec4(1,0,0,1);
    //out_Diffuse = vec4(normal_world,1);
    /*vec3 diffuse = color;
    out_Diffuse = vec4(diffuse,1);
    out_Position = vec4(pass_WorldPosition.xyz, 0);*/
    //out_Color = vec4(moment1,moment2,encode(normal_world));
}