#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=0) uniform sampler2D diffuseMap;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=3) buffer _entities {
	Entity entities[2000];
};
layout(std430, binding=4) buffer _entityOffsets {
	int entityOffsets[2000];
};

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

uniform vec3 probePositionWorld;
uniform int entityIndex = 0;

uniform vec3 directionalLightDirection;
uniform vec3 directionalLightColor;

in vec4 pass_WorldPosition;
in vec4 pass_ProjectedPosition;
in vec3 pass_normal_world;
in float clip;
in vec2 texCoord;
flat in int pass_entityIndex;
//flat in Entity pass_entity;
//flat in Material pass_material;

layout(location=0)out vec4 out_Color;
layout(location=1)out vec4 out_Distance;

mat3 cotangent_frame( vec3 N, vec3 p, vec2 uv )
{
	vec3 dp1 = dFdx( p );
	vec3 dp2 = dFdy( p );
	vec2 duv1 = dFdx( uv );
	vec2 duv2 = dFdy( uv );

	vec3 dp2perp = cross( dp2, N );
	vec3 dp1perp = cross( N, dp1 );
	vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;

	float invmax = inversesqrt( max( dot(T,T), dot(B,B) ) );
	return mat3( T * invmax, B * invmax, N );
}
vec3 perturb_normal(vec3 N, vec3 V, vec2 texcoord, sampler2D normalMap)
{
	vec3 map = (texture(normalMap, texcoord)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}

void main()
{

    if(clip < 0)
        discard;

    vec2 UV = texCoord;
	float depth = pass_ProjectedPosition.z/pass_ProjectedPosition.w;

    float lightDistance = distance(pass_WorldPosition.xyz, probePositionWorld);
//    lightDistance = lightDistance / 250.0;
    depth = lightDistance;
//    gl_FragDepth = 0;

	float moment1 = (depth);
	float moment2 = moment1 * moment1;
	vec4 result;
	result = vec4(moment1, moment2,0,0);

    int entityIndex = pass_entityIndex;
    Entity entity = entities[entityIndex];
    Material material = materials[entity.materialIndex];
    vec3 materialDiffuseColor = vec3(material.diffuseR,
                                     material.diffuseG,
                                     material.diffuseB);
    float materialTransparency = float(material.transparency);
    float materialAmbient = float(material.ambient);
    float alpha = materialTransparency;
    if(material.hasDiffuseMap != 0) {
         sampler2D _diffuseMap = sampler2D(uint64_t(material.handleDiffuse));

        vec4 color = texture(_diffuseMap, UV);
         alpha *= color.a;
         if(color.a<0.1)
         {
             discard;
         }
         materialDiffuseColor.rgb = color.rgb;
    }


	vec3 PN_world = normalize(pass_normal_world);
//	if(material.hasNormalMap != 0) {
//        sampler2D _normalMap = sampler2D(uint64_t(material.handleNormal));
//        PN_world = normalize(perturb_normal(old_PN_world, V, UV, _normalMap));
//    }

    vec3 directionalLightIntensity = materialDiffuseColor.rgb * directionalLightColor * clamp(dot(PN_world, directionalLightDirection), 0, 1);
    out_Color.rgb = directionalLightIntensity + materialAmbient*materialDiffuseColor;
    out_Color.a = lightDistance/100f;
    out_Distance.r = lightDistance/100f;

//	if(gl_Layer == 0) {
//	    out_Color.r = 1;
//	} else if(gl_Layer == 1) {
//	    out_Color.g = 1;
//	}
//	out_Color = vec4(pass_WorldPosition.xyz,0);
//	out_Color = vec4(gl_FragCoord.xy/vec2(512,512),0,0);

//	float dx = dFdx(depth);
//	float dy = dFdy(depth);
	//moment2 += 0.25*(dx*dx+dy*dy);
    //out_Color = vec4(moment1,moment2,packColor(normal_world),1);
    
//    out_Color = vec4(moment1,moment2,0,0);//encode(normal_world));
    //out_Color.rgba = vec4(1,0,0,1);
    //out_Diffuse = vec4(normal_world,1);
    //out_Color = vec4(moment1,moment2,encode(normal_world));
//    out_Color.r = 1;
//    {
//        out_Color.r = 1;
//        out_Color.g = 0;
//        out_Color.b = 1;
//    }
}