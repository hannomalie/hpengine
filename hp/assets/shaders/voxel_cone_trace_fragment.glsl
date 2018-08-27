
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

	vec3 rayVector = worldPos.xyz - startPosition;

	vec3 rayDirection = normalize(rayVector);

	float stepLength = .35;

	vec3 step = rayDirection * stepLength;

	vec3 currentPosition = startPosition;

	vec3 lit = vec3(0,0,0);
	vec3 accumAlbedo = vec3(0,0,0);
	vec3 normalValue = vec3(0,0,0);
	vec3 isStaticValue = vec3(0,0,0);
	float alpha = 0;

	int mipLevel = 0;
	const int NB_STEPS = 1530/(mipLevel+1);
	for (int i = 0; i < NB_STEPS; i++) {
		if(alpha >= 0.01)
		{
			break;
		}
		vec3 gridSizeHalf = ivec3(gridSize/2);
		int mipAdjust = int(pow(mipLevel+1,2));
		vec3 positionSceneScaled = vec3(inverseSceneScale)*currentPosition.xyz;
		positionSceneScaled /= pow(mipLevel+1,2);
		ivec3 gridPosition = ivec3(positionSceneScaled + gridSizeHalf);
		vec4 sampledValue = texelFetch(albedoGrid, gridPosition, mipLevel);
		vec4 sampledNormalValue = texelFetch(normalGrid, gridPosition, mipLevel);
		vec4 sampledLitValue = voxelFetch(grid, gridSize, sceneScale, currentPosition.xyz, mipLevel);//texelFetch(grid, gridPosition, mipLevel);
		normalValue.rgb = sampledNormalValue.rgb;
		lit += sampledLitValue.rgb * sampledLitValue.a;
		isStaticValue = vec3(normalValue.b);
		accumAlbedo += sampledValue.rgb * sampledValue.a;
		alpha += sampledValue.a;
		currentPosition += step;
	}
	return lit.rgb;
}

void main(void) {

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

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    float gridSizeHalf = float(gridSize/2);
    float maxExtent = gridSizeHalf * sceneScale;
    const bool useVoxelConeTracing = true;
    vec3 vct = vec3(0);
    float NdotL = clamp(dot(normalWorld, V), 0, 1);

	const float boost = 1.;
    const bool debugVoxels = false;
    if(!debugVoxels && useVoxelConeTracing &&
        positionWorld.x > -maxExtent && positionWorld.y > -maxExtent && positionWorld.z > -maxExtent &&
        positionWorld.x < maxExtent && positionWorld.y < maxExtent && positionWorld.z < maxExtent) {

        vec4 voxelDiffuse = vec4(0);

        voxelDiffuse = 4*traceVoxelsDiffuse(grid, gridSize, sceneScale, normalWorld, positionWorld+sceneScale*normalWorld);
        float aperture =  tan(0.0003474660443456835 + (roughness * (1.3331290497744692 - (roughness * 0.5040552688878546))));
		vec4 voxelSpecular = 4*voxelTraceCone(grid, gridSize, sceneScale, sceneScale, positionWorld+sceneScale*normalWorld, normalize(reflect(-V, normalWorld)), aperture, 370); // 0.05

        vct += boost*(specularColor.rgb*voxelSpecular.rgb + diffuseColor * voxelDiffuse.rgb);

        const bool useTransparency = false;
        if(useTransparency) {
        	vec3 sampledValue = voxelTraceCone(grid, gridSize, sceneScale, sceneScale, positionWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
//        	compensate missing secondary bounces
//        	sampledValue += 0.1*voxelTraceCone(albedoGrid, gridSize, sceneScale, sceneScale, positionWorld-sceneScale*1*normalWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
            vct = /* small boost factor, figure out why */ 4 * boost * color * sampledValue * (transparency) + vct * opacity;
        }
    }


	if(useAmbientOcclusion){
	    vec4 AOscattering = textureLod(aoScattering, st, 0);
		vct *= clamp(AOscattering.r,0.0,1.0);
	}

    out_DiffuseSpecular.rgb = vct;

    if(debugVoxels) {
    	vct = scatter(eyePosition + normalize(positionWorld-eyePosition), eyePosition);
    	out_DiffuseSpecular.rgb = vct;
    	out_AOReflection.rgb = vct;
    }
}
