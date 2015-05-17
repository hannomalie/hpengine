
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;

//layout(binding=5) uniform sampler2D shadowMap;
//layout(binding=6) uniform sampler2D depthMap;

uniform bool useParallax;
uniform bool useSteepParallax;
uniform float reflectiveness;

uniform float normalMapWidth = 1;

uniform float normalMapHeight = 1;

uniform float diffuseMapWidth = 1;
uniform float diffuseMapHeight = 1;

uniform float specularMapWidth = 1;
uniform float specularMapHeight = 1;


uniform vec3 materialDiffuseColor = vec3(0,0,0);
uniform vec3 materialSpecularColor = vec3(0,0,0);
uniform float materialSpecularCoefficient = 0;
uniform float materialGlossiness = 0;
//uniform vec3 materialAmbientColor = vec3(0,0,0);
//uniform float materialTransparency = 1;

uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 modelMatrix;

uniform vec3 environmentMapWorldPosition = vec3(0,0,0);
uniform vec3 environmentMapMin = vec3(-1,-1,-1);
uniform vec3 environmentMapMax = vec3(1,1,1);

uniform int time = 0;

in vec4 color;
in vec2 texCoord;
in vec3 normalVec;
in vec3 normal_model;
in vec3 normal_world;
in vec3 normal_view;
in vec4 position_clip;
//in vec4 position_clip_uv;
//in vec4 position_clip_shadow;
in vec4 position_world;

in vec3 eyeVec;
in vec3 eyePos_world;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position;
layout(location=1)out vec4 out_normal;
layout(location=2)out vec4 out_color;
layout(location=3)out vec4 out_specular;
layout(location=4)out vec4 out_probe;

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
float linearizeDepth(float z)
{
  float n = near; // camera z near
  float f = far; // camera z far
  return (2.0 * n) / (f + n - z * (f - n));	
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
	vec3 map = (texture2D( normalMap, texcoord )).xyz;
	map = map * 2 - 1;
	mat3 TBN = cotangent_frame( N, -V, texcoord );
	return normalize( TBN * map );
}

vec3 boxProjection(vec3 texCoords3d) {
	vec3 nrdir = normalize(texCoords3d);
	vec3 envMapMin = vec3(-300,-300,-300);
	envMapMin = environmentMapMin;
	vec3 envMapMax = vec3(300,300,300);
	envMapMax = environmentMapMax;
	
	vec3 rbmax = (envMapMax - position_world.xyz)/nrdir;
	vec3 rbmin = (envMapMin - position_world.xyz)/nrdir;
	//vec3 rbminmax = (nrdir.x > 0 && nrdir.y > 0 && nrdir.z > 0) ? rbmax : rbmin;
	vec3 rbminmax;
	rbminmax.x = (nrdir.x>0.0)?rbmax.x:rbmin.x;
	rbminmax.y = (nrdir.y>0.0)?rbmax.y:rbmin.y;
	rbminmax.z = (nrdir.z>0.0)?rbmax.z:rbmin.z;
	float fa = min(min(rbminmax.x, rbminmax.y), rbminmax.z);
	vec3 posonbox = position_world.xyz + nrdir*fa;
	
	//texCoords3d = normalize(posonbox - vec3(0,0,0));
	return normalize(posonbox - environmentMapWorldPosition.xyz);
}
void main(void) {
	
	vec3 V = normalize((position_world.xyz - eyePos_world.xyz).xyz);
	//V = (viewMatrix * vec4(V, 0)).xyz;
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;

#ifdef use_normalMap
		UV.x = texCoord.x * normalMapWidth;
		UV.y = texCoord.y * normalMapHeight;
		//UV = UV + time/2000.0;
#endif

vec2 uvParallax = vec2(0,0);

	if (useParallax) {
		float height = (texture2D(normalMap, UV).rgb).y;//texture2D(heightMap, UV).r;
		height = height * 2 - 1;
		float v = height * 0.014;
		uvParallax = (V.xy * v);
		UV = UV + uvParallax;
	} else if (useSteepParallax) {
		float n = 10;
		float bumpScale = 0.01;
		float step = 1/n;
		vec2 dt = V.xy * bumpScale / (n * V.z);
		
		float height = 1;
		vec2 t = UV;
		vec4 nb = texture2D(normalMap, t);
		while (length(nb.xyz) < height) { 
			height -= step;
			t += dt; 
			nb = texture2D(normalMap, t); 
		}
		UV = t;
	}
	
	// NORMAL
	vec3 PN_view =  (viewMatrix * vec4(normal_model,0)).xyz;
	vec3 PN_world = normalize(normal_world);
#ifdef use_normalMap
	PN_world = normalize(perturb_normal(normal_world, V, UV));
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	out_position = viewMatrix * position_world;
	out_position.w = materialGlossiness;
	float depth = (position_clip.z / position_clip.w);
	
	out_normal = vec4(PN_view, depth);
	
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
	out_color.w = reflectiveness;

	
float n = surface3(vec3(UV, time%1000000 * 0.0001));
float n2 = surface3(vec3(UV, time%1000000 * 0.00001), 2);
out_normal.rgb = (viewMatrix * vec4(normalize(vec3(0,0.8,0)+ 0.5*vec3(n*n2,n*n*n2,n*n2)), 0)).rgb;

#ifdef use_reflectionMap
	out_color.w = length(texture2D(reflectionMap, UV));
#endif
vec3 texCoords3d = normalize(reflect(V, (inverse(viewMatrix) * vec4(out_normal.rgb, 0)).xyz));
texCoords3d = boxProjection(texCoords3d);
//texCoords3d.y *= -1;
out_color.rgb = mix(out_color.rgb, texture(environmentMap, texCoords3d).rgb, 1);//reflectiveness);
out_probe.rgba = texture(environmentMap, texCoords3d).rgba;
if (useParallax) {
	texCoords3d -= texCoords3d * 0.0000001 * 0.0001 * texture(environmentMap, texCoords3d).a;
	texCoords3d = boxProjection(texCoords3d);
	out_probe.rgba = texture(environmentMap, texCoords3d).rgba;
}
//out_color.rgb *= vec3(0.2+0.4*n*n2,0.2+0.4*n*n2,0.2+0.7*n*n2);

vec4 specularColor = vec4(materialSpecularColor, materialSpecularCoefficient);

#ifdef use_specularMap
		UV = texCoord + uvParallax;
		UV.x = texCoord.x * specularMapWidth;
		UV.y = texCoord.y * specularMapHeight;
		vec3 specularSample = texture2D(specularMap, UV).xyz;
		specularColor = vec4(specularSample, materialSpecularCoefficient);
#endif
	out_specular = specularColor;
	
}
