
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;
layout(binding=7) uniform sampler2D visibilityMap;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

in vec4 position_clip;
in vec4 position_view;


//include(globals_structs.glsl)

layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=2) buffer _lights {
	float pointLightCount;
	PointLight pointLights[100];
};

uniform int currentLightIndex;

out vec4 out_DiffuseSpecular;
out vec4 out_AOReflection;

//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

#define kPI 3.1415926536f
const float pointLightRadius = 10.0;

vec3 decodeNormal(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

float calculateAttenuation(float lightRadius, float dist) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}

float chiGGX(float v)
{
    return v > 0 ? 1 : 0;
}

float GGX_PartialGeometryTerm(vec3 v, vec3 n, vec3 h, float alpha)
{
    float VoH2 = clamp(dot(v,h), 0, 1);
    float chi = chiGGX( VoH2 / clamp(dot(v,n), 0, 1) );
    VoH2 = VoH2 * VoH2;
    float tan2 = ( 1 - VoH2 ) / VoH2;
    return (chi * 2) / ( 1 + sqrt( 1 + alpha * alpha * tan2 ) );
}

void main(void) {
	
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	st /= secondPassScale;
  
	vec3 positionView = texture2D(positionMap, st).xyz;

	vec3 color = texture2D(diffuseMap, st).xyz;
	float roughness = texture2D(positionMap, st).w;
	float metallic = texture2D(diffuseMap, st).w;

	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, roughness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = normalize(inverse(viewMatrix) * dir).xyz;
	V = -positionView;

	//skip background
	if (positionView.z > -0.0001) {
		discard;
	}
	
	vec3 normalView = textureLod(normalMap, st, 0).xyz;

	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;

	PointLight pointLight = pointLights[currentLightIndex];
	vec3 lightPositionView = (viewMatrix * vec4(pointLight.positionX, pointLight.positionY, pointLight.positionZ, 1)).xyz;
	vec3 lightDiffuse = vec3(pointLight.colorR, pointLight.colorG, pointLight.colorB);

	vec3 lightDirectionView = normalize(vec4(lightPositionView - positionView, 0)).xyz;
	float attenuation = calculateAttenuation(pointLight.radius, length(lightPositionView - positionView));
	vec3 finalColor;

	int materialIndex = int(textureLod(visibilityMap, st, 0).b);
	Material material = materials[materialIndex];

	if(int(material.materialtype) == 1) {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse,
        								attenuation, V, positionView, normalView,
        								roughness, 0, diffuseColor, specularColor);
		finalColor += attenuation * lightDiffuse * diffuseColor * clamp(dot(-normalView, lightDirectionView), 0, 1);
	} else {
		finalColor = cookTorrance(lightDirectionView, lightDiffuse,
										attenuation, V, positionView, normalView,
										roughness, metallic, diffuseColor, specularColor);
	}

	out_DiffuseSpecular.rgb = 4 * finalColor;
}
