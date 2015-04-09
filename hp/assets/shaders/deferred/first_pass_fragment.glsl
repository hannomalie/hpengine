
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;
layout(binding=7) uniform sampler2D roughnessMap;

uniform bool useParallax;
uniform bool useSteepParallax;

uniform float normalMapWidth = 1;
uniform float normalMapHeight = 1;

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

uniform float specularMapWidth = 1;
uniform float specularMapHeight = 1;

uniform float roughnessMapWidth = 1;
uniform float roughnessMapHeight = 1;

uniform vec3 materialDiffuseColor = vec3(0,0,0);
uniform vec3 materialSpecularColor = vec3(0,0,0);
uniform float materialSpecularCoefficient = 0;
uniform float materialRoughness = 0;
uniform float materialMetallic = 0;
uniform float materialAmbient = 0;
uniform int probeIndex1 = 0;
uniform int probeIndex2 = 0;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform int time = 0;
uniform bool useRainEffect = false;
uniform float rainEffect = 0.0;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec3 tangent_world;
in vec3 bitangent_world;
in vec4 position_clip;
in vec4 position_clip_last;
//in vec4 position_clip_uv;
//in vec4 position_clip_shadow;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;
//in mat3 TBN;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility

vec3 mod289(vec3 x)
{
  return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 mod289(vec4 x)
{
  return x - floor(x * (1.0 / 289.0)) * 289.0;
}

vec4 permute(vec4 x)
{
  return mod289(((x*34.0)+1.0)*x);
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
vec3 perturb_normal( vec3 N, vec3 V, vec2 texcoord )
{
	vec3 map = (textureLod(normalMap, texcoord, 0)).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, V, texcoord );
	return normalize( TBN * map );
}

#define kPI 3.1415926536f
vec2 encodeNormal(vec3 n) {
    return vec2((vec2(atan(n.y,n.x)/kPI, n.z)+1.0)*0.5);
}

void main(void) {
	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 motionVec = (position_clip_post_w.xy) - (position_clip_last_post_w.xy);
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

	out_position = viewMatrix * position_world;
	
#ifdef use_normalMap
		UV.x = UV.x * normalMapWidth;
		UV.y = UV.y * normalMapHeight;
		//UV = UV + time/2000.0;
#endif

	vec2 uvParallax = vec2(0,0);

	//if (useParallax) {
#ifdef use_heightMap
	if (true) {
		float height = (textureLod(normalMap, UV,0).rgb).y;//texture2D(heightMap, UV).r;
		height = height * 2 - 1;
		height = clamp(height, 0, 1);
		height = (textureLod(heightMap, UV,0).rgb).r;
		
		mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
		vec3 viewVectorTangentSpace = normalize((inverse(TBN)) * (out_position.rgb));
		float v = height * 0.04 - 0.02;
		uvParallax = (viewVectorTangentSpace.xy * v);
		UV = UV - uvParallax;
	//} else if (useSteepParallax) {
	} else {
   	   // determine required number of layers
	   const float minLayers = 10;
	   const float maxLayers = 15;
	   const float parallaxScale = 0.1;
	   mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
	   vec3 viewVectorTangentSpace = normalize((inverse(TBN)) * (out_position.rgb));
	   float numLayers = mix(maxLayers, minLayers, abs(dot(vec3(0, 0, 1), V)));
	
	   // height of each layer
	   float layerHeight = 1.0 / numLayers;
	   // depth of current layer
	   float currentLayerHeight = 0;
	   // shift of texture coordinates for each iteration
	   vec2 dtex = parallaxScale * viewVectorTangentSpace.xy / viewVectorTangentSpace.z / numLayers;
	
	   // current texture coordinates
	   vec2 currentTextureCoords = UV;
	
	   // depth from heightmap
	   float heightFromTexture = texture(heightMap, currentTextureCoords).r;
	
	   // while point is above surface
	   while(heightFromTexture > currentLayerHeight) {
	      // go to the next layer
	      currentLayerHeight += layerHeight; 
	      // shift texture coordinates along V
	      currentTextureCoords -= dtex;
	      // new depth from heightmap
	      heightFromTexture = texture(heightMap, currentTextureCoords).r;
	   }
	
	   ///////////////////////////////////////////////////////////
	   // Start of Relief Parallax Mapping
	
	   // decrease shift and height of layer by half
	   vec2 deltaTexCoord = dtex / 2;
	   float deltaHeight = layerHeight / 2;
	
	   // return to the mid point of previous layer
	   currentTextureCoords += deltaTexCoord;
	   currentLayerHeight -= deltaHeight;
	
	   // binary search to increase precision of Steep Paralax Mapping
	   const int numSearches = 5;
	   for(int i=0; i<numSearches; i++)
	   {
	      // decrease shift and height of layer by half
	      deltaTexCoord /= 2;
	      deltaHeight /= 2;
	
	      // new depth from heightmap
	      heightFromTexture = texture(heightMap, currentTextureCoords).r;
	
	      // shift along or agains vector V
	      if(heightFromTexture > currentLayerHeight) // below the surface
	      {
	         currentTextureCoords -= deltaTexCoord;
	         currentLayerHeight += deltaHeight;
	      }
	      else // above the surface
	      {
	         currentTextureCoords += deltaTexCoord;
	         currentLayerHeight -= deltaHeight;
	      }
	   }
	
	   // return results
	   float parallaxHeight = currentLayerHeight;
	   UV = currentTextureCoords;
	}
	
#endif
	
    mat3 TBN = transpose(mat3(
        (vec4(normalize(tangent_world),0)).xyz,
        (vec4(normalize(bitangent_world),0)).xyz,
        normalize(normal_world)
    ));
	
	// NORMAL
	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
#ifdef use_normalMap
	PN_world = normalize(perturb_normal(PN_world, V, UV));
	//PN_world = inverse(TBN) * normalize((texture(normalMap, UV)*2-1).xyz);
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	float depth = (position_clip.z / position_clip.w);
	
	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);
	
	vec4 color = vec4(materialDiffuseColor, 1);
#ifdef use_diffuseMap
	UV = texCoord;
	UV.x = texCoord.x * diffuseMapWidth;
	UV.y = texCoord.y * diffuseMapHeight;
	UV += uvParallax;
	color = texture2D(diffuseMap, UV);
	if(color.a<0.1)
	{
		discard;
	}
#endif
  	out_color = color;
  	out_color.w = materialMetallic;
  	
#ifdef use_occlusionMap
	//out_color.rgb = clamp(out_color.rgb - texture2D(occlusionMap, UV).xyz, 0, 1);
#endif

	out_position.w = materialRoughness;
#ifdef use_roughnessMap
	UV.x = texCoord.x * roughnessMapWidth;
	UV.y = texCoord.y * roughnessMapHeight;
	UV = texCoord + uvParallax;
	float r = texture2D(roughnessMap, UV).x;
	out_position.w = materialRoughness*r;
#endif
	
#ifdef use_specularMap
	UV.x = texCoord.x * specularMapWidth;
	UV.y = texCoord.y * specularMapHeight;
	UV = texCoord + uvParallax;
	vec3 specularSample = texture2D(specularMap, UV).xyz;
	float glossiness = length(specularSample)/length(vec3(1,1,1));
	const float glossinessBias = 1.5;
	out_position.w = clamp(glossinessBias-glossiness, 0, 1) * (materialRoughness);
#endif

  	out_motion = vec4(motionVec,depth,probeIndex2);
  	out_normal.a = materialAmbient;
  	out_visibility = vec4(1,depth,depth,0);
  	
  	if(RAINEFFECT) {
		float n = surface3(vec3(UV, 0.01));
		float n2 = surface3(vec3(UV, 0.1), 2);
		float waterEffect = rainEffect * clamp(3 * n2 * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0)*clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		float waterEffect2 = rainEffect * clamp(3 * n * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		out_position.w *= 1-waterEffect2;
		out_color.rgb *= mix(vec3(1,1,1), vec3(1,1,1+waterEffect/8), waterEffect2);
		out_color.w = waterEffect2;
  	}
  		
}
