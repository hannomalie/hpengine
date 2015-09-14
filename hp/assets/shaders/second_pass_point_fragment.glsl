
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D specularMap;
layout(binding=4) uniform samplerCube environmentMap;
layout(binding=5) uniform sampler2D probe;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

uniform vec3 lightPosition;
uniform float lightRadius;
uniform vec3 lightDiffuse;
uniform vec3 lightSpecular;

in vec4 position_clip;
in vec4 position_view;

struct Pointlight {
	vec3 _position;
	float _radius;
	vec3 _diffuse;
	vec3 _specular;
};
//uniform int lightCount;
uniform int currentLightIndex;
layout(std140) uniform pointlights {
	Pointlight lights[1000];
};

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

float calculateAttenuation(float dist) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - distDivRadius, 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return atten_factor;
}
float __calculateAttenuation(float dist) {
    float distDivRadius = (dist / lightRadius);
    float atten_factor = clamp(1.0f - pow(distDivRadius,4), 0.0, 1.0);
    atten_factor = pow(atten_factor, 2);
    return 100*atten_factor/(dist*dist); // TODO: Figure out the 100...
}
float _calculateAttenuation(float dist) {
	float cutoff = lightRadius;
	float r = lightRadius/10;
	float d = max(dist - r, 0);
	
	float denom = d/r + 1;
    float attenuation = 1 / (denom*denom);
    attenuation = (attenuation - cutoff) / (1 - cutoff);
    attenuation = max(attenuation, 0);
    
    return attenuation;
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
	V = positionView;

	//skip background
	if (positionView.z > -0.0001) {
		discard;
	}
	
	vec3 normalView = textureLod(normalMap, st, 0).xyz;

	vec4 specular = texture2D(specularMap, st);
	float depth = texture2D(normalMap, st).w;

	vec3 lightPositionView = (viewMatrix * vec4(lightPosition, 1)).xyz;
	vec3 lightDirectionView = normalize(vec4(lightPositionView - positionView, 0)).xyz;
	vec3 lightDiffuse = lightDiffuse;

	float attenuation = calculateAttenuation(length(lightPositionView - positionView));

	vec3 finalColor = cookTorrance(lightDirectionView, lightDiffuse,
								attenuation, V, positionView, normalView,
								roughness, metallic, diffuseColor, specularColor);
	
	out_DiffuseSpecular.rgb = 4 * finalColor;
}
