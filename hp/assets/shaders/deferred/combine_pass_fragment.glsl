#version 420

layout(binding=0) uniform sampler2D diffuseMap; // diffuse, reflectiveness 
layout(binding=1) uniform sampler2D lightAccumulationMap; // diffuse, specular
layout(binding=2) uniform sampler2D aoReflection; // ao, reflectedColor
layout(binding=3) uniform sampler2D specularMap; // specular color
layout(binding=4) uniform sampler2D positionMap; // position, glossiness
layout(binding=5) uniform sampler2D normalMap; // normal, depth
layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2

uniform mat4 shadowMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 projectionMatrix;


uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform vec3 ambientColor = vec3(0.5,0.5,0.5);

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
	
	return result/8;

}
float chebyshevUpperBound(float dist, vec4 ShadowCoordPostW)
{
	// We retrive the two moments previously stored (depth and depth*depth)
	vec2 moments = texture2D(shadowMap,ShadowCoordPostW.xy).rg;
	moments = blurSample(shadowMap, ShadowCoordPostW.xy, moments.y/100).rg;
	moments += blurSample(shadowMap, ShadowCoordPostW.xy, moments.y/50).rg;
	moments /= 2;
	// Surface is fully lit. as the current fragment is before the light occluder
	if (dist <= moments.x)
		return 1.0 ;

	// The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
	// How likely this pixel is to be lit (p_max)
	float variance = moments.y - (moments.x*moments.x);
	variance = max(variance,0.002);

	float d = dist - moments.x;
	float p_max = variance / (variance + d*d);

	return p_max;
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
  	
  	vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
  	positionShadow.xyz /= positionShadow.w;
  	float depth = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
  	float momentum2 = texture2D(shadowMap, positionShadow.xy).g;
  	float depthShadow = blurSample(shadowMap, positionShadow.xy, momentum2/20).r;
  	if (positionShadow.x < 0 || positionShadow.x > 1 || positionShadow.y < 0 || positionShadow.y > 1) {
		depth = 1;
	}
	
  	float glossiness = positionGlossiness.w;
  	
  	vec4 colorReflectiveness = texture2D(diffuseMap, st);
  	vec3 color = colorReflectiveness.xyz;
  	float reflectiveness = colorReflectiveness.w;
  	
  	vec4 specularColorPower = texture2D(specularMap, st);
  	vec3 specularColor = specularColorPower.xyz;
  	
	vec4 lightDiffuseSpecular = texture2D(lightAccumulationMap, st);
	lightDiffuseSpecular = blurSample(lightAccumulationMap, st, 0.002 * length(lightDiffuseSpecular));
	float specularFactor = lightDiffuseSpecular.a;
	
	vec4 aoReflect = texture2D(aoReflection, st);
	float ao = blurSample(aoReflection, st, 0.005).r;
	//vec3 reflectedColor = aoReflect.gba;
	vec3 reflectedColor = blurSample(aoReflection, st, glossiness/250).gba;
	
	/*color *=1;
	color.rgb = Uncharted2Tonemap(4*color.rgb);
	vec3 whiteScale = vec3(1.0,1.0,1.0)/Uncharted2Tonemap(vec3(11.2,11.2,11.2));
	color.rgb = color.rgb * whiteScale;
	//color.r = pow(color.r, 1/2.2);
	//color.g = pow(color.g, 1/2.2);
	//color.b = pow(color.b, 1/2.2);
	
	reflectedColor *=1;
	reflectedColor.rgb = Uncharted2Tonemap(4*reflectedColor.rgb);
	reflectedColor.rgb = reflectedColor.rgb * whiteScale;
	//reflectedColor.r = pow(reflectedColor.r, 1/2.2);
	//reflectedColor.g = pow(reflectedColor.g, 1/2.2);
	//reflectedColor.b = pow(reflectedColor.b, 1/2.2);*/
	
	vec3 ambientTerm = ambientColor * ao;
	vec3 specularTerm = specularColor * pow(max(specularFactor,0.0), specularColorPower.a);
	vec4 lit = vec4(mix(color + specularTerm, reflectedColor+ specularTerm, reflectiveness), 1);
	out_color = lit;
	//out_color.rgb = reflectedColor;

	float visibility = 1.0;

	visibility = clamp(chebyshevUpperBound(depth, positionShadow), 0, 1);
	out_color *= vec4(ambientTerm, 1) + visibility * vec4(lightDiffuseSpecular.xyz, 1);
}