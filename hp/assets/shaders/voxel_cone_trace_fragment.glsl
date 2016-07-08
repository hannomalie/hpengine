
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=8) uniform samplerCubeArray probes;

layout(binding=11) uniform sampler2D aoScattering;

layout(binding=12) uniform sampler3D albedoGrid;
layout(binding=13) uniform sampler3D grid;
layout(binding=14) uniform sampler3D normalGrid;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};

uniform float sceneScale = 1;
uniform float inverseSceneScale = 1;
uniform int gridSize;

uniform float screenWidth = 1280;
uniform float screenHeight = 720;
uniform float secondPassScale = 1;

uniform bool useAmbientOcclusion = true;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;
uniform mat4 shadowMatrix;

uniform vec3 eyePosition;
uniform vec3 lightDirection;
uniform vec3 lightDiffuse;
uniform float scatterFactor = 1;

uniform int activeProbeCount;
uniform vec3 environmentMapMin[100];
uniform vec3 environmentMapMax[100];

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_DiffuseSpecular;
layout(location=1)out vec4 out_AOReflection;

//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 scatter(vec3 worldPos, vec3 startPosition) {
	const int NB_STEPS = 2530;

	vec3 rayVector = worldPos.xyz - startPosition;

	vec3 rayDirection = normalize(rayVector);

	float stepLength = 0.125;

	vec3 step = rayDirection * stepLength;

	vec3 currentPosition = startPosition;

	vec3 lit = vec3(0,0,0);
	vec3 accumAlbedo = vec3(0,0,0);
	vec3 normalValue = vec3(0,0,0);
	vec3 isStaticValue = vec3(0,0,0);
	float alpha = 0;

	for (int i = 0; i < NB_STEPS; i++) {
		if(alpha >= 1.0) { break; }
		int mipLevel = 0;
		vec3 gridSizeHalf = ivec3(gridSize/2);
		ivec3 gridPosition = ivec3(vec3(inverseSceneScale)*currentPosition.xyz + gridSizeHalf);
		vec4 sampledValue = texelFetch(albedoGrid, gridPosition, mipLevel);
		vec4 sampledNormalValue = texelFetch(normalGrid, gridPosition, mipLevel);
		vec4 sampledLitValue = texelFetch(grid, gridPosition, mipLevel);
		normalValue.rgb = sampledNormalValue.rgb;
		lit = sampledLitValue.rgb;
		isStaticValue = vec3(normalValue.b);
		accumAlbedo += sampledValue.rgb * sampledValue.a;
		alpha += sampledValue.a;
		currentPosition += step;
	}
	return lit;
}

void main(void) {

	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;
  	st /= secondPassScale;

	float depth = texture(normalMap, st).w;
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	//vec4 positionViewPreW = (inverse(projectionMatrix)*vec4(st, depth, 1));
	//positionView = positionViewPreW.xyz / positionViewPreW.w;

    vec4 colorTransparency = texture2D(diffuseMap, st);
	vec3 color = colorTransparency.xyz;
	float roughness = texture2D(positionMap, st).a;

  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	V = positionView;
	V = -normalize((positionWorld.xyz - eyePosition.xyz).xyz);

	// skip background
	if (positionView.z > -0.0001) {
	  discard;
	}
	vec4 normalAmbient = texture(normalMap, st);
	vec3 normalView = normalAmbient.xyz;
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView,0.0)).xyz;

	float metallic = textureLod(diffuseMap, st, 0).a;

    vec4 motionVecProbeIndices = texture(motionMap, st);
  	vec2 motion = motionVecProbeIndices.xy;
  	float transparency = motionVecProbeIndices.a;

	float opacity = mix(1-transparency, 1, metallic);

	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.02), maxSpecular, glossiness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    float gridSizeHalf = float(gridSize/2);
    float maxExtent = gridSizeHalf * sceneScale;
    const bool useVoxelConeTracing = true;
    vec3 vct;

    const bool debugVoxels = false;
    if(!debugVoxels && useVoxelConeTracing &&
        positionWorld.x > -maxExtent && positionWorld.y > -maxExtent && positionWorld.z > -maxExtent &&
        positionWorld.x < maxExtent && positionWorld.y < maxExtent && positionWorld.z < maxExtent) {

//        out_color.rgb = voxelFetch(ivec3(positionWorld), 0).rgb;
//        out_color.rgb = 100*traceVoxels(positionWorld, camPosition, 3).rgb;

        vec4 voxelDiffuse;// = 8f*voxelTraceCone(grid, 2, positionWorld, normalize(normalWorld), 5, 100);

        const int SAMPLE_COUNT = 4;
        voxelDiffuse = traceVoxelsDiffuse(SAMPLE_COUNT, grid, gridSize, sceneScale, normalWorld, positionWorld);
		vec4 voxelSpecular = voxelTraceCone(grid, gridSize, sceneScale, sceneScale, positionWorld, normalize(reflect(-V, normalWorld)), 0.1*roughness, 370); // 0.05

//
//        out_color.rgb += specularColor.rgb*voxelSpecular.rgb * (1-roughness) + color*voxelDiffuse.rgb * (1 - (1-roughness));
        vct += 1*(specularColor.rgb*voxelSpecular.rgb + color*voxelDiffuse.rgb);


        const bool useTransparency = false;
        if(useTransparency) {
            vct = 1*voxelTraceCone(grid, gridSize, sceneScale, 1, positionWorld+3*normalWorld, normalize(refract(-V, normalWorld,2-roughness)), 0.1*roughness, 370).rgb * (transparency) + vct * opacity;
        }
    }

	if(useAmbientOcclusion) {
	    vec4 AOscattering = textureLod(aoScattering, st, 0);
		vct *= clamp(AOscattering.r,0.0,1.0);
	}

    out_DiffuseSpecular.rgb = vct;

    if(debugVoxels) {
    	vct = scatter(eyePosition + normalize(positionWorld-eyePosition), eyePosition);
    	out_DiffuseSpecular.rgb = vct;
    }
}
