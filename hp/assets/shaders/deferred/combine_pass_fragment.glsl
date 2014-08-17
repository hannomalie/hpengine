#version 420

layout(binding=0) uniform sampler2D diffuseMap; // diffuse, reflectiveness 
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D aoReflection; // ao, reflectedColor
layout(binding=3) uniform sampler2D specularMap; // specular color
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth

uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 projectionMatrix;


uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);
uniform int exposure = 4;

in vec3 position;
in vec2 texCoord;

out vec4 out_color;
vec3 Uncharted2Tonemap(vec3 x)
{
    float A = 0.15;
	float B = 0.50;
	float C = 0.10;
	float D = 0.20;
	float E = 0.02;
	float F = 0.30;

    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F))-E/F;
}
vec4 blurSample(sampler2D sampler, vec2 texCoord, float dist) {

	vec4 result = texture2D(sampler, texCoord);
	
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y + dist));
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y));
	result += texture2D(sampler, vec2(texCoord.x + dist, texCoord.y - dist));
	result += texture2D(sampler, vec2(texCoord.x, texCoord.y - dist));
	
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y + dist));
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y));
	result += texture2D(sampler, vec2(texCoord.x - dist, texCoord.y - dist));
	result += texture2D(sampler, vec2(texCoord.x, texCoord.y + dist));
	
	return result/9;

}

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	
  	vec4 positionGlossiness = texture2D(positionMap, st);
  	vec3 positionView = positionGlossiness.xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
  	
  	float glossiness = positionGlossiness.w;
  	
  	vec4 colorReflectiveness = texture2D(diffuseMap, st);
  	vec3 color = colorReflectiveness.xyz * exposure/2;
  	float reflectiveness = colorReflectiveness.w;
  	
  	vec4 specularColorPower = texture2D(specularMap, st);
  	vec3 specularColor = specularColorPower.xyz;
  	
	vec4 lightDiffuseSpecular = texture2D(lightAccumulationMap, st);
	lightDiffuseSpecular = blurSample(lightAccumulationMap, st, 0);
	float specularFactor = lightDiffuseSpecular.a;
	
	vec4 aoReflect = texture2D(aoReflection, st);
	float ao = blurSample(aoReflection, st, 0.0025).r;
	ao += blurSample(aoReflection, st, 0.000125).r;
	ao /= 2;

	//vec3 reflectedColor = aoReflect.gba;
	vec3 reflectedColor = blurSample(aoReflection, st, glossiness/250).gba;
	
	vec3 specularTerm = specularColor * pow(max(specularFactor,0.0), specularColorPower.a);
	vec3 finalColor = mix(color, reflectedColor, reflectiveness);
	//finalColor.rgb = Uncharted2Tonemap(2*finalColor.rgb);
	vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(11.2,11.2,11.2));
	finalColor.rgb = finalColor.rgb * whiteScale;
	//finalColor.r = pow(finalColor.r,1/2.2);
	//finalColor.g = pow(finalColor.g,1/2.2);
	//finalColor.b = pow(finalColor.b,1/2.2);
	vec3 ambientTerm = ambientColor/3 * ao * finalColor.rgb;
	vec4 lit = vec4(ambientTerm, 1) + vec4(lightDiffuseSpecular.rgb*finalColor, 1) + vec4(specularTerm,1);
	out_color = lit;
	//out_color.rgb = vec3(ao,ao,ao);
	//out_color.rgb = color.xyz;
}
