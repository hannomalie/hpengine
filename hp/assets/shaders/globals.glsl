#define kPI 3.1415926536f

const float kernel[9] = { 1.0/16.0, 2.0/16.0, 1.0/16.0,
				2.0/16.0, 4.0/16.0, 2.0/16.0,
				1.0/16.0, 2.0/16.0, 1.0/16.0 };


vec3 hemisphereSample_uniform(float u, float v, vec3 N) {
    const float PI = 3.1415926536;
     float phi = u * 2.0 * PI;
     float cosTheta = 1.0 - v;
     float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
     vec3 result = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);

	vec3 UpVector = abs(N.z) < 0.999 ? vec3(0,0,1) : vec3(1,0,0);
	vec3 TangentX = normalize( cross( UpVector, N ) );
	vec3 TangentY = cross( N, TangentX );
	 // Tangent to world space
	 result = TangentX * result.x + TangentY * result.y + N * result.z;
     //mat3 transform = createOrthonormalBasis(N);
	 //result = (transform) * result;

     return result;
}

float linstep(float low, float high, float v){
    return clamp((v-low)/(high-low), 0.0, 1.0);
}

float radicalInverse_VdC(uint bits) {
     bits = (bits << 16u) | (bits >> 16u);
     bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
     bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
     bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
     bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);

     return float(bits) * 2.3283064365386963e-10; // / 0x100000000
}
vec2 hammersley2d(uint i, int N) {
	return vec2(float(i)/float(N), radicalInverse_VdC(i));
}

vec4 blur(sampler2DArray sampler, vec3 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.025);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * texture(sampler, texCoords + vec3(-blurDistance, -blurDistance, 0));
	result += kernel[1] * texture(sampler, texCoords + vec3(0, -blurDistance, 0));
	result += kernel[2] * texture(sampler, texCoords + vec3(blurDistance, -blurDistance, 0));

	result += kernel[3] * texture(sampler, texCoords + vec3(-blurDistance, 0, 0));
	result += kernel[4] * texture(sampler, texCoords + vec3(0, 0, 0));
	result += kernel[5] * texture(sampler, texCoords + vec3(blurDistance, 0, 0));

	result += kernel[6] * texture(sampler, texCoords + vec3(-blurDistance, blurDistance, 0));
	result += kernel[7] * texture(sampler, texCoords + vec3(0, -blurDistance, 0));
	result += kernel[8] * texture(sampler, texCoords + vec3(blurDistance, blurDistance, 0));

	return result;
}

vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0125);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * texture(sampler, texCoords + vec2(-blurDistance, -blurDistance));
	result += kernel[1] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[2] * texture(sampler, texCoords + vec2(blurDistance, -blurDistance));

	result += kernel[3] * texture(sampler, texCoords + vec2(-blurDistance));
	result += kernel[4] * texture(sampler, texCoords + vec2(0, 0));
	result += kernel[5] * texture(sampler, texCoords + vec2(blurDistance, 0));

	result += kernel[6] * texture(sampler, texCoords + vec2(-blurDistance, blurDistance));
	result += kernel[7] * texture(sampler, texCoords + vec2(0, -blurDistance));
	result += kernel[8] * texture(sampler, texCoords + vec2(blurDistance, blurDistance));

	return result;
}

vec4 blur(sampler2D sampler, vec2 texCoords, float inBlurDistance, float mipmap) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0025);
	vec4 result = vec4(0,0,0,0);
	result += kernel[0] * textureLod(sampler, texCoords + vec2(-blurDistance, -blurDistance), mipmap);
	result += kernel[1] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipmap);
	result += kernel[2] * textureLod(sampler, texCoords + vec2(blurDistance, -blurDistance), mipmap);

	result += kernel[3] * textureLod(sampler, texCoords + vec2(-blurDistance), mipmap);
	result += kernel[4] * textureLod(sampler, texCoords + vec2(0, 0), mipmap);
	result += kernel[5] * textureLod(sampler, texCoords + vec2(blurDistance, 0), mipmap);

	result += kernel[6] * textureLod(sampler, texCoords + vec2(-blurDistance, blurDistance), mipmap);
	result += kernel[7] * textureLod(sampler, texCoords + vec2(0, -blurDistance), mipmap);
	result += kernel[8] * textureLod(sampler, texCoords + vec2(blurDistance, blurDistance), mipmap);

	return result;
}
vec3 mod289(vec3 x)
{
	return x-floor(x * (1.0 / 289.0)) * 289.0;
}
vec2 mod289(vec2 x)
{
	return x-floor(x * (1.0 / 289.0)) * 289.0;
}
vec3 permute(vec3 x)
{
	return mod289(((x*34.0)+1.0)*x);
}
vec4 permute(vec4 x)
{
	return mod((34.0 * x + 1.0) * x, 289.0);
}
float snoise(vec2 v)
{
	const vec4 C=vec4(0.211324865405187, // (3.0-sqrt(3.0))/6.0
	0.366025403784439, // 0.5*(sqrt(3.0)-1.0)
	-0.577350269189626 , // -1.0 + 2.0 * C.x
	0.024390243902439); // 1.0 / 41.0
	// First corner
	vec2 i=floor(v + dot(v, C.yy));
	vec2 x0 = v - i + dot(i, C.xx);
	// Other corners
	vec2 i1 = (x0.x > x0.y) ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
	vec4 x12 = x0.xyxy + C.xxzz;
	x12.xy -= i1;
	// Permutations
	i = mod289(i); // Avoid truncation effects in permutation
	vec3 p = permute( permute( i.y + vec3(0.0, i1.y, 1.0 ))
	+ i.x + vec3(0.0, i1.x, 1.0 ));
	vec3 m=max(0.5 - vec3(dot(x0,x0), dot(x12.xy,x12.xy),
	dot(x12.zw,x12.zw)), 0.0);
	m=m*m;
	m=m*m;
	// Gradients
	vec3 x = 2.0 * fract(p * C.www) - 1.0;
	vec3 h=abs(x) - 0.5;
	vec3 a0 = x - floor(x + 0.5);
	// Normalize gradients implicitly by scaling m
	m *= 1.79284291400159 - 0.85373472095314 * ( a0*a0 + h*h );
	// Compute final noise value at P
	vec3 g;
	g.x = a0.x * x0.x + h.x * x0.y;
	g.yz = a0.yz * x12.xz + h.yz * x12.yw;
	return 130.0 * dot(m, g);
}

vec4 taylorInvSqrt(vec4 r)
{
  return 1.79284291400159 - 0.85373472095314 * r;
}

vec3 fade(vec3 t) {
  return t*t*t*(t*(t*6.0-15.0)+10.0);
}
float cnoise(vec3 P)
{
  vec3 Pi0 = floor(P); // Integer part for indexing
  vec3 Pi1 = Pi0 + vec3(1.0); // Integer part + 1
  Pi0 = mod289(Pi0);
  Pi1 = mod289(Pi1);
  vec3 Pf0 = fract(P); // Fractional part for interpolation
  vec3 Pf1 = Pf0 - vec3(1.0); // Fractional part - 1.0
  vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
  vec4 iy = vec4(Pi0.yy, Pi1.yy);
  vec4 iz0 = Pi0.zzzz;
  vec4 iz1 = Pi1.zzzz;

  vec4 ixy = permute(permute(ix) + iy);
  vec4 ixy0 = permute(ixy + iz0);
  vec4 ixy1 = permute(ixy + iz1);

  vec4 gx0 = ixy0 * (1.0 / 7.0);
  vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
  gx0 = fract(gx0);
  vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
  vec4 sz0 = step(gz0, vec4(0.0));
  gx0 -= sz0 * (step(0.0, gx0) - 0.5);
  gy0 -= sz0 * (step(0.0, gy0) - 0.5);

  vec4 gx1 = ixy1 * (1.0 / 7.0);
  vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
  gx1 = fract(gx1);
  vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
  vec4 sz1 = step(gz1, vec4(0.0));
  gx1 -= sz1 * (step(0.0, gx1) - 0.5);
  gy1 -= sz1 * (step(0.0, gy1) - 0.5);

  vec3 g000 = vec3(gx0.x,gy0.x,gz0.x);
  vec3 g100 = vec3(gx0.y,gy0.y,gz0.y);
  vec3 g010 = vec3(gx0.z,gy0.z,gz0.z);
  vec3 g110 = vec3(gx0.w,gy0.w,gz0.w);
  vec3 g001 = vec3(gx1.x,gy1.x,gz1.x);
  vec3 g101 = vec3(gx1.y,gy1.y,gz1.y);
  vec3 g011 = vec3(gx1.z,gy1.z,gz1.z);
  vec3 g111 = vec3(gx1.w,gy1.w,gz1.w);

  vec4 norm0 = taylorInvSqrt(vec4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
  g000 *= norm0.x;
  g010 *= norm0.y;
  g100 *= norm0.z;
  g110 *= norm0.w;
  vec4 norm1 = taylorInvSqrt(vec4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
  g001 *= norm1.x;
  g011 *= norm1.y;
  g101 *= norm1.z;
  g111 *= norm1.w;

  float n000 = dot(g000, Pf0);
  float n100 = dot(g100, vec3(Pf1.x, Pf0.yz));
  float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
  float n110 = dot(g110, vec3(Pf1.xy, Pf0.z));
  float n001 = dot(g001, vec3(Pf0.xy, Pf1.z));
  float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
  float n011 = dot(g011, vec3(Pf0.x, Pf1.yz));
  float n111 = dot(g111, Pf1);

  vec3 fade_xyz = fade(Pf0);
  vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
  vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
  float n_xyz = mix(n_yz.x, n_yz.y, fade_xyz.x);
  return 2.2 * n_xyz;
}

float surface3 ( vec3 coord ) {
    float frequency = 4.0;
    float n = 0.0;

    n += 1.0    * abs( cnoise( coord * frequency ) );
    n += 0.5    * abs( cnoise( coord * frequency * 2.0 ) );
    n += 0.25   * abs( cnoise( coord * frequency * 4.0 ) );

    return n;
}
float surface3 ( vec3 coord, float frequency) {
    float n = 0.0;

    n += 1.0    * abs( cnoise( coord * frequency ) );
    n += 0.5    * abs( cnoise( coord * frequency * 2.0 ) );
    n += 0.25   * abs( cnoise( coord * frequency * 4.0 ) );

    return n;
}


vec2 encodeNormal(vec3 n) {
    return vec2((vec2(atan(n.y,n.x)/kPI, n.z)+1.0)*0.5);
}

float packColor(vec3 color) {
    return color.r + color.g * 256.0 + color.b * 256.0 * 256.0;
}
vec3 unpackColor(float f) {
    vec3 color;
    color.b = floor(f / 256.0 / 256.0);
    color.g = floor((f - color.b * 256.0 * 256.0) / 256.0);
    color.r = floor(f - color.b * 256.0 * 256.0 - color.g * 256.0);
    // now we have a vec3 with the 3 components in range [0..256]. Let's normalize it!
    return color / 256.0;
}

// http://aras-p.info/texts/CompactNormalStorage.html
vec3 decode(vec2 enc) {
    vec2 ang = enc*2-1;
    vec2 scth;
    scth.x = sin(ang.x * kPI);
    scth.y = cos(ang.x * kPI);
    vec2 scphi = vec2(sqrt(1.0 - ang.y*ang.y), ang.y);
    return vec3(scth.y*scphi.x, scth.x*scphi.x, scphi.y);
}

const vec3 pSphere[16] = vec3[](vec3(0.53812504, 0.18565957, -0.43192),vec3(0.13790712, 0.24864247, 0.44301823),vec3(0.33715037, 0.56794053, -0.005789503),vec3(-0.6999805, -0.04511441, -0.0019965635),vec3(0.06896307, -0.15983082, -0.85477847),vec3(0.056099437, 0.006954967, -0.1843352),vec3(-0.014653638, 0.14027752, 0.0762037),vec3(0.010019933, -0.1924225, -0.034443386),vec3(-0.35775623, -0.5301969, -0.43581226),vec3(-0.3169221, 0.106360726, 0.015860917),vec3(0.010350345, -0.58698344, 0.0046293875),vec3(-0.08972908, -0.49408212, 0.3287904),vec3(0.7119986, -0.0154690035, -0.09183723),vec3(-0.053382345, 0.059675813, -0.5411899),vec3(0.035267662, -0.063188605, 0.54602677),vec3(-0.47761092, 0.2847911, -0.0271716));
float rand(vec2 co){
	return 0.5+(fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453))*0.5;
}

vec3 cookTorrance(in vec3 lightDirectionView, in vec3 lightDiffuse, in float attenuation,
                  in vec3 ViewVector, in vec3 positionView, in vec3 normalView,
                  float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(ViewVector);
	V = -normalize(positionView);
 	vec3 L = normalize(lightDirectionView);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normalView);
    vec3 P = positionView;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);

	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	//roughness = (roughness+1)/2;
	float alpha = roughness*roughness;
	float alphaSquare = alpha*alpha;

	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float denom = (NdotH*NdotH*(alphaSquare-1))+1;
	float D = alphaSquare/(3.1416*denom*denom);

	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));

    // Schlick
    float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2, electrics up to 1 and mostly above 0.5
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(L, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;

	vec3 diff = diffuseColor * lightDiffuse.rgb * NdotL;

	/////////////////////////
	// OREN-NAYAR
	/*{
		float angleVN = acos(NdotV);
	    float angleLN = acos(NdotL);
	    float alpha = max(angleVN, angleLN);
	    float beta = min(angleVN, angleLN);
	    float gamma = dot(V - N * dot(V, L), L - N * NdotL);
	    float roughnessSquared = alpha;
	    float A = 1.0 - 0.5 * (roughnessSquared / (roughnessSquared + 0.57));
	    float B = 0.45 * (roughnessSquared / (roughnessSquared + 0.09));
	    float C = sin(alpha) * tan(beta);
	    diff *= (A + B * max(0.0, gamma) * C);
    }*/
	/////////////////////////

	diff = diff * (1-fresnel); // enegy conservation between diffuse and spec http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/

	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);

	return attenuation * (diff + cookTorrance * lightDiffuse.rgb * specularColor);
}
vec3[2] cookTorranceCubeMap(samplerCube cubemap,
                  in vec3 ViewVectorWorld, in vec3 positionWorld, in vec3 normalWorld,
                  float roughness, float metallic, vec3 diffuseColor, vec3 specularColor) {
//http://renderman.pixar.com/view/cook-torrance-shader
//http://www.filmicworlds.com/2014/04/21/optimizing-ggx-shaders-with-dotlh/
	vec3 V = normalize(ViewVectorWorld);
 	vec3 L = normalize(-normalWorld);
    vec3 H = normalize(L + V);
    vec3 N = normalize(normalWorld);
    vec3 P = positionWorld;
    float NdotH = clamp(dot(N, H), 0.0, 1.0);
    float NdotV = clamp(dot(N, V), 0.0, 1.0);
    float NdotL = clamp(dot(N, L), 0.0, 1.0);
    float VdotH = clamp(dot(V, H), 0.0, 1.0);

	// UE4 roughness mapping graphicrants.blogspot.de/2013/03/08/specular-brdf-reference.html
	//roughness = (roughness+1)/2;
	float alpha = roughness*roughness;
	float alphaSquare = alpha*alpha;

	// GGX
	//http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/
	float denom = (NdotH*NdotH*(alphaSquare-1))+1;
	float D = alphaSquare/(3.1416*denom*denom);

	float G = min(1, min((2*NdotH*NdotV/VdotH), (2*NdotH*NdotL/VdotH)));

    // Schlick
    float F0 = 0.02;
	// Specular in the range of 0.02 - 0.2, electrics up to 1 and mostly above 0.5
	// http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float glossiness = (1-roughness);
	float maxSpecular = mix(0.2, 1.0, metallic);
	F0 = max(F0, (glossiness*maxSpecular));
	//F0 = max(F0, metallic*0.2);
    float fresnel = 1; fresnel -= dot(L, H);
	fresnel = pow(fresnel, 5.0);
	//http://seblagarde.wordpress.com/2011/08/17/feeding-a-physical-based-lighting-mode/
	float temp = 1.0; temp -= F0;
	fresnel *= temp;
	float F = fresnel + F0;

    vec3 lightDiffuse = textureLod(cubemap, normalWorld, roughness * 8).rgb;
    vec3 lightSpecular = textureLod(cubemap, reflect(V, normalWorld), roughness * 8).rgb;
	vec3 diff = diffuseColor * lightDiffuse.rgb;

	/////////////////////////
	// OREN-NAYAR
	/*{
		float angleVN = acos(NdotV);
	    float angleLN = acos(NdotL);
	    float alpha = max(angleVN, angleLN);
	    float beta = min(angleVN, angleLN);
	    float gamma = dot(V - N * dot(V, L), L - N * NdotL);
	    float roughnessSquared = alpha;
	    float A = 1.0 - 0.5 * (roughnessSquared / (roughnessSquared + 0.57));
	    float B = 0.45 * (roughnessSquared / (roughnessSquared + 0.09));
	    float C = sin(alpha) * tan(beta);
	    diff *= (A + B * max(0.0, gamma) * C);
    }*/
	/////////////////////////

	diff = diff * (1-fresnel); // enegy conservation between diffuse and spec http://www.gamedev.net/topic/638197-cook-torrance-brdf-general/

	float cookTorrance = clamp((F*D*G/(4*(NdotL*NdotV))), 0.0, 1.0);

    vec3[2] result;
    result[0] = diff;
    result[1] = 1 * lightSpecular * specularColor;
	return result;
}


vec4 voxelFetch(sampler3D grid, int gridSize, float sceneScale, vec3 positionWorld, float loD) {
    const int gridSizeHalf = gridSize/2;
    float inverseSceneScale = 1.f/sceneScale;

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    if(any(greaterThan(positionGridScaled, vec3(gridSizeHalf))) ||
       any(lessThan(positionGridScaled, -vec3(gridSizeHalf)))) {

//       return vec4(0);
    }

    int level = int(loD);
    vec3 positionAdjust = vec3(gridSize/pow(2, level+1));
    float positionScaleFactor = pow(2, level);

    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(gridSize)+vec3(0.5);

    vec4 result = textureLod(grid, samplePositionNormalized, loD);

    return result;

//    return textureLod(grid, samplePositionNormalized, level);
}
vec4 voxelFetchBlurred(sampler3D grid, int gridSize, float sceneScale, vec3 positionWorld, float loD) {
    const int gridSizeHalf = gridSize/2;
    float inverseSceneScale = 1.f/sceneScale;

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    if(any(greaterThan(positionGridScaled, vec3(gridSizeHalf))) ||
       any(lessThan(positionGridScaled, -vec3(gridSizeHalf)))) {

       return vec4(0);
    }

    int level = int(loD);
    vec3 positionAdjust = vec3(gridSize/pow(2, level+1));
    float positionScaleFactor = pow(2, level);

    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(gridSize)+vec3(0.5);

    vec4 result = textureLod(grid, samplePositionNormalized, level);

    float amount = 0.005;
    result *= 0.2;
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(amount, amount, amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(-amount, amount, amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(-amount, -amount, amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(-amount, -amount, -amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(amount, amount, -amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(amount, -amount, -amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(-amount, amount, -amount), loD);
    result += 0.1*textureLod(grid, samplePositionNormalized + vec3(amount, -amount, amount), loD);

    return result;
}

vec4 voxelTraceCone(sampler3D grid, int gridSize, float sceneScale, float minVoxelDiameter, vec3 origin, vec3 dir, float coneRatio, float maxDist) {
	float minVoxelDiameterInv = 1.0/minVoxelDiameter;
	vec3 samplePos = origin;
	vec4 accum = vec4(0.0);
	float minDiameter = minVoxelDiameter;
	float startDist = minDiameter;
	float dist = startDist;
	vec4 ambientLightColor = vec4(0.);
	vec4 fadeCol = ambientLightColor*vec4(0, 0, 0, 0.2);
	while (dist <= maxDist && accum.w < .9)
	{
		float sampleDiameter = max(minDiameter, coneRatio * dist);
		float sampleLOD = log2(sampleDiameter * minVoxelDiameterInv);
		vec3 samplePos = origin + dir * dist;
//		sampleLOD = 3f;
		vec4 sampleValue = voxelFetch(grid, gridSize, sceneScale, samplePos-dir, sampleLOD);
		sampleValue = mix(sampleValue,fadeCol, clamp(dist/maxDist-0.25, 0.0, 1.0));
		float sampleWeight = (1.0 - accum.w);
		accum += sampleValue * sampleWeight;
		dist += sampleDiameter;
	}
	return accum;
}


vec4 specConeTrace(sampler3D grid, int gridSize, float sceneScale, vec3 o, vec3 dir, float coneRatio, float maxDist)
{
//    float sceneScale = 2;
//     float inverseSceneScale = 1f/sceneScale;
//     o = vec3(inverseSceneScale) * o;
    float voxDim = 256;
	vec3 samplePos = o;
	vec4 accum = vec4(0.0);
	float minDiam = 1.0/voxDim;
	float startDist = 2*minDiam;

	float dist = startDist;
	while(dist <= maxDist && accum.w < 1.0)
	{
		float sampleDiam = max(minDiam, coneRatio*dist);
		float sampleLOD = log2(sampleDiam*voxDim);
		vec3 samplePos = o + dir*dist;
//		sampleLOD = 1.5;
		vec4 sampleVal = voxelFetch(grid, gridSize, sceneScale, samplePos-dir, sampleLOD);//sampleSpecVox(samplePos, -d, sampleLOD);

		float sampleWt = (1.0 - accum.w);
		accum += sampleVal * sampleWt;
		dist += sampleDiam;
	}

	accum.xyz *= 2.0;

	return accum;
}

vec4 traceVoxels(sampler3D grid, int gridSize, float sceneScale, vec3 worldPos, vec3 startPosition, float lod) {
	const int NB_STEPS = 30;

	vec3 rayVector = worldPos.xyz - startPosition;

	float rayLength = length(rayVector);
	vec3 rayDirection = rayVector / rayLength;

	float stepLength = rayLength / NB_STEPS;

	vec3 step = rayDirection * stepLength;
	vec3 currentPosition = startPosition;

	vec4 accumFog = vec4(0);

    int stepCount = 0;
	for (int i = 0; i < NB_STEPS; i++) {
	    stepCount++;
	    if(accumFog.a >= 0.99f) { break; }
        accumFog += voxelFetch(grid, gridSize, sceneScale, currentPosition, lod);
		currentPosition += step;
	}
	accumFog /= stepCount;
	return accumFog;
}


vec4 traceVoxelsDiffuse(int SAMPLE_COUNT, sampler3D grid, int gridSize, float sceneScale, vec3 normalWorld, vec3 positionWorld) {
    vec4 voxelDiffuse;
    for (int k = 0; k < SAMPLE_COUNT; k++) {
        const float PI = 3.1415926536;
        vec2 Xi = hammersley2d(k, SAMPLE_COUNT);
        float Phi = 2 * PI * Xi.x;
        float a = 1;//roughness;
        float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )) );
        float SinTheta = sqrt( 1 - CosTheta * CosTheta );

        vec3 H;
        H.x = SinTheta * cos( Phi );
        H.y = SinTheta * sin( Phi );
        H.z = CosTheta;
        H = hemisphereSample_uniform(Xi.x, Xi.y, normalWorld);

        float dotProd = clamp(dot(normalWorld, H),0,1);
        voxelDiffuse += vec4(dotProd) * voxelTraceCone(grid, gridSize, sceneScale, sceneScale/2, positionWorld, normalize(H), sceneScale/float(SAMPLE_COUNT), 150);
    }


//    vec3 tangent = cross(normalWorld, normalWorld == vec3(0,1,0) ? vec3(1,0,0) : vec3(0,1,0));
//    vec3 bitangent = cross(normalWorld, tangent);
//    float minVoxelDiameter = 6*sceneScale;
//    float coneRatio = 6;
////https://github.com/domme/VoxelConeTracing/blob/master/bin/assets/shader/finalRenderFrag.shader
//  voxelDiffuse += voxelTraceCone(grid, gridSize, sceneScale, minVoxelDiameter, positionWorld, normalize(normalWorld), coneRatio, 150);
//  voxelDiffuse += 0.707 * voxelTraceCone(grid, gridSize, sceneScale, minVoxelDiameter, positionWorld, normalize(normalWorld + tangent), coneRatio, 150);
//  voxelDiffuse += 0.707 * voxelTraceCone(grid, gridSize, sceneScale, minVoxelDiameter, positionWorld, normalize(normalWorld - tangent), coneRatio, 150);
//  voxelDiffuse += 0.707 * voxelTraceCone(grid, gridSize, sceneScale, minVoxelDiameter, positionWorld, normalize(normalWorld + bitangent), coneRatio, 150);
//  voxelDiffuse += 0.707 * voxelTraceCone(grid, gridSize, sceneScale, minVoxelDiameter, positionWorld, normalize(normalWorld - bitangent), coneRatio, 150);

//https://github.com/thefranke/dirtchamber/blob/master/shader/vct_tools.hlsl
//    vec3 diffdir = normalize(normalWorld.zxy);
//    vec3 crossdir = cross(normalWorld.xyz, diffdir);
//    vec3 crossdir2 = cross(normalWorld.xyz, crossdir);
//
//    // jitter cones
//    float j = 1.0 + (fract(sin(dot(vec2(0.5, 0.5), vec2(12.9898, 78.233))) * 43758.5453)) * 0.2;
//
//    vec3 directions[9] =
//    {
//        normalWorld,
//        normalize(crossdir   * j + normalWorld),
//        normalize(-crossdir  * j + normalWorld),
//        normalize(crossdir2  * j + normalWorld),
//        normalize(-crossdir2 * j + normalWorld),
//        normalize((crossdir + crossdir2)  * j + normalWorld),
//        normalize((crossdir - crossdir2)  * j + normalWorld),
//        normalize((-crossdir + crossdir2) * j + normalWorld),
//        normalize((-crossdir - crossdir2) * j + normalWorld),
//    };
//
//    float diff_angle = 0.6f;
//
//    vec4 diffuse = vec4(0, 0, 0, 0);
//
//    for (uint d = 0; d < 9; ++d)
//    {
//        vec3 D = directions[d];
//
//        float NdotL = clamp(dot(normalize(normalWorld), normalize(D)), 0, 1);
//
//        float minDiameter = 1.f;
//        voxelDiffuse += voxelTraceCone(grid, gridSize, sceneScale, minDiameter, positionWorld, normalize(D), 0.5, 50) * NdotL;
//    }
    return voxelDiffuse;
}


vec2 cartesianToSpherical(vec3 cartCoords){
	float a = atan(cartCoords.y/cartCoords.x);
	float b = atan(sqrt(cartCoords.x*cartCoords.x+cartCoords.y*cartCoords.y))/cartCoords.z;
	return vec2(a, b);
}

vec3 sphericalToCartesian(vec2 spherical){
	vec3 outCart;
    outCart.x = cos(spherical.x) * sin(spherical.y);
    outCart.y = sin(spherical.x) * sin(spherical.y);
    outCart.z = cos(spherical.y);

    return outCart;
}

// https://knarkowicz.wordpress.com/2014/04/16/octahedron-normal-vector-encoding/
vec2 OctWrap( vec2 v )
{
    return ( 1.0 - abs( v.yx ) ) * ( vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0 ));
}

vec2 Encode( vec3 n )
{
    n /= ( abs( n.x ) + abs( n.y ) + abs( n.z ) );
    n.xy = n.z >= 0.0 ? n.xy : OctWrap( n.xy );
    n.xy = n.xy * 0.5 + 0.5;
    return n.xy;
}

vec3 Decode( vec2 encN )
{
    encN = encN * 2.0 - 1.0;

    vec3 n;
    n.z = 1.0 - abs( encN.x ) - abs( encN.y );
    n.xy = n.z >= 0.0 ? encN.xy : OctWrap( encN.xy );
    n = normalize( n );
    return n;
}

#define STEREOSCALE 1.77777f
#define INVSTEREOSCALE (1.0f/STEREOSCALE)
//vec2 cartesianToSpherical(vec3 n) {
//    n = normalize(-n);
//    vec2 enc = (n.xy / (n.z+1)) * INVSTEREOSCALE;
//    return enc;
//}
//vec3 sphericalToCartesian(vec2 enc) {
//    float scale = STEREOSCALE;
//    vec3 nn = vec3(enc.xy * vec2(scale,scale), 0);
//    nn.z = 1.0f;
//    float g = 2.0 / dot(nn.xyz,nn.xyz);
//    vec3 n;
//    n.xy = g*nn.xy;
//    n.z = g-1;
//    return normalize(-n);
//}

vec2 scaleLightmapCoords(vec3 in_LightmapTextureCoord, float lightmapWidth, float lightmapHeight) {
    vec2 textureSpaceCoords = in_LightmapTextureCoord.xy / vec2(lightmapWidth, lightmapHeight); // between 0 and 1
    return textureSpaceCoords;
}
