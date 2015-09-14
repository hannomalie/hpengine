
layout(binding=0) uniform sampler2D diffuseMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D specularMap;
layout(binding=3) uniform sampler2D occlusionMap;
layout(binding=4) uniform sampler2D heightMap;
layout(binding=5) uniform sampler2D reflectionMap;
layout(binding=6) uniform samplerCube environmentMap;
layout(binding=7) uniform sampler2D roughnessMap;

uniform int entityIndex;
uniform bool isSelected = false;

uniform bool useParallax;
uniform bool useSteepParallax;

uniform float parallaxScale = 0.04;
uniform float parallaxBias = 0.02;

uniform vec3 materialDiffuseColor = vec3(0,0,0);
uniform vec3 materialSpecularColor = vec3(0,0,0);
uniform float materialSpecularCoefficient = 0;
uniform float materialRoughness = 0;
uniform float materialMetallic = 0;
uniform float materialAmbient = 0;
uniform float materialTransparency = 0;
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
in mat3 TBN;
uniform float near = 0.1;
uniform float far = 100.0;

layout(location=0)out vec4 out_position; // position, roughness
layout(location=1)out vec4 out_normal; // normal, depth
layout(location=2)out vec4 out_color; // color, metallic
layout(location=3)out vec4 out_motion; // motion, probeIndices
layout(location=4)out vec4 out_visibility; // visibility

//include(globals.glsl)

void main(void) {	
	vec3 V = -normalize((position_world.xyz + eyePos_world.xyz).xyz);
	//V = normalize((eyePos_world.xyz - position_world.xyz).xyz);
	vec2 UV = texCoord;
	
	vec4 position_clip_post_w = position_clip/position_clip.w; 
	vec4 position_clip_last_post_w = position_clip_last/position_clip_last.w;
	vec2 motionVec = (position_clip_post_w.xy) - (position_clip_last_post_w.xy);
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	V = (inverse(viewMatrix) * dir).xyz;
	
	vec2 positionTextureSpace = position_clip_post_w.xy * 0.5 + 0.5;

	out_position = viewMatrix * position_world;
	
#ifdef use_normalMap
		UV.x = UV.x;
		UV.y = UV.y;
		//UV = UV + time/2000.0;
#endif

	vec2 uvParallax = vec2(0,0);

#define use_precomputed_tangent_space_
	//if (useParallax) {
#ifdef use_heightMap
	if (true) {
		float height = (texture(heightMap, UV).rgb).r;

#ifdef use_precomputed_tangent_space
		vec3 viewVectorTangentSpace = normalize((TBN) * (V));
		float v = height * parallaxScale - parallaxBias;
#else
		mat3 TBN = cotangent_frame( normalize(normal_world), V, UV );
		vec3 viewVectorTangentSpace = -normalize((TBN) * (V));
		float v = height * parallaxScale - parallaxBias;
		v = clamp(0, v, v);
#endif
		uvParallax = (v * viewVectorTangentSpace.xy);
		UV = UV + uvParallax;
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
	
	// NORMAL
	vec3 PN_view = normalize(viewMatrix * vec4(normal_world,0)).xyz;
	vec3 PN_world = normalize(normal_world);
	vec3 old_PN_world = PN_world;

#ifdef use_normalMap
#ifdef use_precomputed_tangent_space
	PN_world = transpose(TBN) * normalize((texture(normalMap, UV)*2-1).xyz);
#else
	PN_world = normalize(perturb_normal(old_PN_world, V, UV));
#endif
	PN_view = normalize((viewMatrix * vec4(PN_world, 0)).xyz);
#endif
	
	float depth = (position_clip.z / position_clip.w);
	
	out_normal = vec4(PN_view, depth);
	//out_normal = vec4(PN_world*0.5+0.5, depth);
	//out_normal = vec4(encodeNormal(PN_view), environmentProbeIndex, depth);
	
	vec4 color = vec4(materialDiffuseColor, 1);
#ifdef use_diffuseMap
	UV = texCoord;
	UV.x = texCoord.x;
	UV.y = texCoord.y;
	UV += uvParallax;
	color = texture(diffuseMap, UV);
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
	UV.x = texCoord.x;
	UV.y = texCoord.y;
	UV = texCoord + uvParallax;
	float r = texture2D(roughnessMap, UV).x;
	out_position.w = materialRoughness*r;
#endif
	
#ifdef use_specularMap
	UV.x = texCoord.x * specular;
	UV.y = texCoord.y * specular;
	UV = texCoord + uvParallax;
	vec3 specularSample = texture2D(specularMap, UV).xyz;
	float glossiness = length(specularSample)/length(vec3(1,1,1));
	const float glossinessBias = 1.5;
	out_position.w = clamp(glossinessBias-glossiness, 0, 1) * (materialRoughness);
#endif

  	out_motion = vec4(motionVec,depth,materialTransparency);
  	out_normal.a = materialAmbient;
  	out_visibility = vec4(1,depth,depth,entityIndex);
  	
  	if(RAINEFFECT) {
		float n = surface3(vec3(UV, 0.01));
		float n2 = surface3(vec3(UV, 0.1), 2);
		float waterEffect = rainEffect * clamp(3 * n2 * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0)*clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		float waterEffect2 = rainEffect * clamp(3 * n * clamp(dot(PN_world, vec3(0,1,0)), 0.0, 1.0), 0.0, 1.0);
		out_position.w *= 1-waterEffect2;
		out_color.rgb *= mix(vec3(1,1,1), vec3(1,1,1+waterEffect/8), waterEffect2);
		out_color.w = waterEffect2;
  	}
  	
	if(isSelected)
	{
		out_color.rgb = vec3(1,0,0);
	}
	//out_color.rgb = vec3(1,0,0);
	//out_normal.a = 1;
}
