
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=8) uniform samplerCubeArray probes;

layout(binding=11) uniform sampler2D aoScattering;

layout(binding=13) uniform sampler3D grid;
layout(binding=14) uniform sampler3D normalGrid;
layout(binding=15) uniform sampler3D albedoGrid;

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
    const bool useVoxelConeTracing = true;
    vec3 vct;
    if(useVoxelConeTracing && positionGridScaled.x > -gridSizeHalf && positionGridScaled.y > -gridSizeHalf && positionGridScaled.z > -gridSizeHalf &&
        positionGridScaled.x < gridSizeHalf && positionGridScaled.y < gridSizeHalf && positionGridScaled.z < gridSizeHalf) {

//        out_color.rgb = voxelFetch(ivec3(positionWorld), 0).rgb;
//        out_color.rgb = 100*traceVoxels(positionWorld, camPosition, 3).rgb;

        //vec4 voxelTraceCone(grid, float minVoxelDiameter, vec3 origin, vec3 dir, float coneRatio, float maxDist)
        vec4 voxelSpecular = voxelTraceCone(grid, gridSize, sceneScale, 1, positionWorld, normalize(reflect(-V, normalWorld)), 0.1*roughness, 370); // 0.05
        vec4 voxelDiffuse;// = 8f*voxelTraceCone(grid, 2, positionWorld, normalize(normalWorld), 5, 100);

        const int SAMPLE_COUNT = 4;
        voxelDiffuse = traceVoxelsDiffuse(SAMPLE_COUNT, grid, gridSize, sceneScale, normalWorld, positionWorld);

//
//        out_color.rgb += specularColor.rgb*voxelSpecular.rgb * (1-roughness) + color*voxelDiffuse.rgb * (1 - (1-roughness));
        vct += 4*(specularColor.rgb*voxelSpecular.rgb + color*voxelDiffuse.rgb);


        const bool useTransparency = false;
        if(useTransparency) {
            vct = 4*voxelTraceCone(grid, gridSize, sceneScale, 1, positionWorld, normalize(refract(-V, normalWorld,2-roughness)), 0.1*roughness, 370).rgb * (transparency) + vct * opacity;
        }
    }

	if(useAmbientOcclusion) {
	    vec4 AOscattering = textureLod(aoScattering, st, 0);
		vct *= clamp(AOscattering.r,0.0,1.0);
	}

//	vct = voxelTraceCone(albedoGrid, gridSize, sceneScale, 1, positionWorld, normalize(reflect(-V, normalWorld)), 0.01, 370).rgb;
    out_DiffuseSpecular.rgb = vct;
}
