layout(binding=0) uniform sampler2D diffuseMap;

uniform vec3 pointLightPositionWorld;

in vec4 pass_WorldPosition;
in vec4 pass_ProjectedPosition;
in float clip;
in vec2 texCoord;

out vec4 out_Color;
out vec4 out_Diffuse;
out vec4 out_Position;

void main()
{

    if(clip < 0)
        discard;

	float depth = pass_ProjectedPosition.z/pass_ProjectedPosition.w;

    float lightDistance = length(pass_WorldPosition.xyz - pointLightPositionWorld);
//    lightDistance = lightDistance / 250.0;
    depth = lightDistance;
//    gl_FragDepth = lightDistance;

	float moment1 = (depth);
	float moment2 = moment1 * moment1;
	vec4 result;
	result = vec4(moment1, moment2,0,0);
	out_Color = result;

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
    /*vec3 diffuse = color;
    if(hasDiffuseMap) {
    	vec2 UV;
		UV.x = texCoord.x * diffuseMapWidth;
		UV.y = texCoord.y * diffuseMapHeight;
    	diffuse = texture(diffuseMap, UV).rgb;
    }
    out_Diffuse = vec4(diffuse,1);
    out_Position = vec4(pass_WorldPosition.xyz, 0);*/
    //out_Color = vec4(moment1,moment2,encode(normal_world));
}