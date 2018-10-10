#extension GL_NV_gpu_shader5 : enable
#extension GL_ARB_bindless_texture : enable

layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=8) uniform samplerCubeArray probes;

layout(binding=11) uniform sampler2D aoScattering;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[100];
};
layout(std430, binding=5) buffer _voxelGrids {
    int size;
    int dummy0;
    int dummy1;
    int dummy2;
	VoxelGrid voxelGrids[10];
};

uniform float screenWidth = 1280;
uniform float screenHeight = 720;

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

uniform bool debugVoxels;

in vec2 pass_TextureCoord;
layout(location=0)out vec4 out_DiffuseSpecular;
layout(location=1)out vec4 out_AOReflection;

//include(globals_structs.glsl)
//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 scatter(vec3 worldPos, vec3 startPosition, int gridSize, float sceneScale, sampler3D albedoGrid, sampler3D normalGrid, sampler3D grid, VoxelGrid voxelGrid) {
	vec3 rayVector = worldPos.xyz - startPosition;

	vec3 rayDirection = normalize(rayVector);

	float stepLength = .35;

	vec3 step = rayDirection * stepLength;

	vec3 currentPosition = startPosition;

	vec3 lit = vec3(0,0,0);
	vec3 accumAlbedo = vec3(0,0,0);
	vec3 normalValue = vec3(0,0,0);
	vec3 isStaticValue = vec3(0,0,0);
	float accumAlpha = 0;

	float mipLevel = 0f;
	const int NB_STEPS = int(1530/(mipLevel+1));
	const float stepSize = 0.5f*(mipLevel+1);
	for (int i = 0; i < NB_STEPS; i++) {
		if(accumAlpha >= 1.0f)
		{
			break;
		}
		if(!isInsideVoxelGrid(currentPosition, voxelGrid)) {
		    continue;
		}
		vec3 gridSizeHalf = ivec3(gridSize/2);
		ivec3 positionInGridSpace = worldToGridPosition(currentPosition, voxelGrid, int(mipLevel));
		vec4 sampledValue = voxelFetchXXX(voxelGrid, albedoGrid, currentPosition, mipLevel);
		vec4 sampledNormalValue = voxelFetchXXX(voxelGrid, normalGrid, currentPosition, mipLevel);
		vec4 sampledLitValue = voxelFetchXXX(voxelGrid, grid, currentPosition, mipLevel);
		normalValue.rgb = sampledNormalValue.rgb;
		lit += sampledLitValue.rgb * sampledLitValue.a;
		isStaticValue = vec3(normalValue.b);
		float alpha = 1 - sampledValue.a;
		accumAlbedo += sampledValue.rgb * alpha;
		accumAlpha += sampledValue.a * alpha;
		currentPosition += step*stepSize;
	}
	accumAlbedo *= accumAlpha;
	return accumAlbedo.rgb;
}

void main(void) {

    VoxelGrid voxelGrid = voxelGrids[0];
    float sceneScale = voxelGrid.scale;
    float inverseSceneScale = 1f / voxelGrid.scale;
    int gridSize = voxelGrid.resolution;

    sampler3D albedoGrid = sampler3D(uint64_t(voxelGrid.albedoGridHandle));
    sampler3D normalGrid = sampler3D(uint64_t(voxelGrid.normalGridHandle));
    sampler3D grid = sampler3D(uint64_t(voxelGrid.gridHandle));

	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

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
	vec3 maxSpecular = mix(vec3(0.05), color, metallic);
	vec3 specularColor = mix(vec3(0.00), maxSpecular, glossiness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

    const bool useVoxelConeTracing = true;
    vec3 vct = vec3(0);
    float NdotL = clamp(dot(normalWorld, V), 0, 1);

	const float boost = 1.;
    if(!debugVoxels && useVoxelConeTracing) {

        vec4 voxelDiffuse = vec4(0);

        voxelDiffuse = 4*traceVoxelsDiffuseXXX(voxelGrid, grid, normalWorld, positionWorld+sceneScale*normalWorld);
        float aperture =  tan(0.0003474660443456835 + (roughness * (1.3331290497744692 - (roughness * 0.5040552688878546))));
		vec4 voxelSpecular = 4*voxelTraceConeXXX(voxelGrid, grid, positionWorld+sceneScale*normalWorld, normalize(reflect(-V, normalWorld)), aperture, 370); // 0.05

        vct += boost*(specularColor.rgb*voxelSpecular.rgb + diffuseColor * voxelDiffuse.rgb);

        const bool useTransparency = false;
        if(useTransparency) {
        	vec3 sampledValue = voxelTraceConeXXX(voxelGrid, grid, positionWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
//        	compensate missing secondary bounces
//        	sampledValue += 0.1*voxelTraceConeXXX(albedoGrid, gridSize, sceneScale, sceneScale, positionWorld-sceneScale*1*normalWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
            vct = /* small boost factor, figure out why */ 4 * boost * color * sampledValue * (transparency) + vct * opacity;
        }
    }


	if(useAmbientOcclusion){
	    vec4 AOscattering = textureLod(aoScattering, st, 0);
		vct *= clamp(AOscattering.r,0.0,1.0);
	}


    if(debugVoxels) {
    	vct = scatter(eyePosition + normalize(positionWorld-eyePosition), eyePosition, gridSize, sceneScale, albedoGrid, normalGrid, grid, voxelGrid);
//    	int lod = 0;
//    	vct = texelFetch(albedoGrid, worldToGridPosition(positionWorld, voxelGrid, lod), lod).rgb;
    	out_AOReflection.rgb = vct;
    	if(!isInsideVoxelGrid(positionWorld, voxelGrid)) {
    	    vct.rgb = vec3(1,0,0);
    	}
    }
    out_DiffuseSpecular.rgb = vct;
}
