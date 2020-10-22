layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=8) uniform samplerCubeArray probes;

layout(binding=11) uniform sampler2D aoScattering;

#if defined(BINDLESSTEXTURES) && defined(SHADER5)
#else
layout(binding=9) uniform sampler3D grid;
layout(binding=12) uniform sampler3D albedoGrid;
layout(binding=13) uniform sampler3D normalGrid;
#endif

uniform int voxelGridIndex = 0;
uniform int voxelGridCount = 0;

//include(globals_structs.glsl)
layout(std430, binding=1) buffer _materials {
	Material materials[];
};
layout(std430, binding=5) buffer _voxelGrids {
    VoxelGrid[10] voxelGrids;
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
uniform int skyBoxMaterialIndex = 0;

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

#if defined(BINDLESSTEXTURES) && defined(SHADER5)
vec3 scatter(vec3 worldPos, vec3 startPosition, VoxelGrid[MAX_VOXELGRIDS] voxelGridArray) {
	vec3 rayVector = worldPos.xyz - startPosition;

	vec3 rayDirection = normalize(rayVector);

	vec3 currentPosition = startPosition;

	vec3 lit = vec3(0,0,0);
	vec3 lit2 = vec3(0,0,0);
	vec3 accumAlbedo = vec3(0,0,0);
	vec3 normalValue = vec3(0,0,0);
	vec3 isStaticValue = vec3(0,0,0);
	float accumAlpha = 0.0f;

	float mipLevel = 0.0f;
	const int NB_STEPS = int(1530/(mipLevel+1));
	const float stepSize = 0.25f*(mipLevel+1);
	vec3 step = rayDirection * stepSize;
	for (int i = 0; i < NB_STEPS; i++) {
		if(accumAlpha >= 1.0f)
		{
			break;
		}

//TODO: Make this a struct please
        vec3 step_lit = vec3(0,0,0);
        vec3 step_lit2 = vec3(0,0,0);
        vec3 step_accumAlbedo = vec3(0,0,0);
        vec3 step_normalValue = vec3(0,0,0);
        vec3 step_isStaticValue = vec3(0,0,0);
        float step_accumAlpha = 0.0f;

		float minScale = 1000000.0;
        for(int voxelGridIndex = 0; voxelGridIndex < voxelGridCount; voxelGridIndex++) {
            VoxelGrid voxelGrid = voxelGrids[voxelGridIndex];

            if(!isInsideVoxelGrid(currentPosition, voxelGrid)) {
                continue;
            }
//            If we don't have a hit yet minScale is still -1
            if(voxelGrid.scale > minScale) {
                continue;
            }

            minScale = voxelGrid.scale;
            vec4 sampledValue = voxelFetch(voxelGrid, toSampler(voxelGrid.albedoGridHandle), currentPosition, mipLevel);
            vec4 sampledNormalValue = voxelFetch(voxelGrid, toSampler(voxelGrid.normalGridHandle), currentPosition, mipLevel);
            vec4 sampledLitValue = voxelFetch(voxelGrid, toSampler(voxelGrid.gridHandle), currentPosition, mipLevel);
            step_normalValue.rgb = sampledNormalValue.rgb;
            step_lit = sampledLitValue.rgb * sampledLitValue.a;
            step_isStaticValue = vec3(normalValue.b);
            float alpha = 1 - sampledValue.a;
            step_accumAlbedo = sampledValue.rgb * alpha;
            step_accumAlpha = sampledValue.a * alpha;
        }


        lit += step_lit;
        accumAlbedo += step_accumAlbedo;
        normalValue = step_normalValue.rgb;
        isStaticValue = step_isStaticValue;
        accumAlpha += step_accumAlpha;

        currentPosition += step;
	}
	accumAlbedo *= accumAlpha;
	return lit.rgb;
}
#else
vec3 scatter(vec3 worldPos, vec3 startPosition, VoxelGrid[MAX_VOXELGRIDS] voxelGridArray) {
    return vec3(0.0f);
}
#endif


#if defined(BINDLESSTEXTURES) && defined(SHADER5)
vec4 voxelTraceConeXXX(VoxelGrid[MAX_VOXELGRIDS] voxelGridArray, int gridIndex, vec3 origin, vec3 dir, float coneRatio, float maxDist) {

    vec4 accum = vec4(0.0);
    float alpha = 0;
    float dist = 0;
    vec3 samplePos = origin;// + dir;

    while (dist <= maxDist && alpha < 1.0)
    {
        float minScale = 100000.0;
        int canditateIndex = -1;
        VoxelGrid voxelGrid;
        for(int voxelGridIndex = 0; voxelGridIndex < voxelGridCount; voxelGridIndex++) {
            VoxelGrid candidate = voxelGridArray[voxelGridIndex];
            if(isInsideVoxelGrid(samplePos, candidate) && candidate.scale < minScale) {
                canditateIndex = voxelGridIndex;
                minScale = candidate.scale;
                voxelGrid = candidate;
            }
        }

        float minVoxelDiameter = 0.25f*voxelGrid.scale;
        float minVoxelDiameterInv = 1.0/minVoxelDiameter;
        vec4 ambientLightColor = vec4(0.);
        float diameter = max(minVoxelDiameter, 2 * coneRatio * (1+dist));
        float increment = diameter;

        if(canditateIndex != -1) {
            sampler3D grid;
            if(gridIndex == ALBEDOGRID) {
                grid = toSampler(voxelGrid.albedoGridHandle);
            } else if(gridIndex == NORMALGRID) {
                grid = toSampler(voxelGrid.normalGridHandle);
            } else {
                grid = toSampler(voxelGrid.gridHandle);
            }
            int gridSize = voxelGrid.resolution;

            float sampleLOD = log2(diameter * minVoxelDiameterInv);
            vec4 sampleValue = voxelFetch(voxelGrid, grid, samplePos, sampleLOD);

            accum.rgb += sampleValue.rgb;
            float a = 1 - alpha;
            alpha += a * sampleValue.a;
        }

        dist += increment;
        samplePos = origin + dir * dist;
        increment *= 1.25f;
    }
	return vec4(accum.rgb, alpha);
}
#else
vec4 voxelTraceConeXXX(VoxelGridArray voxelGridArray, int gridIndex, vec3 origin, vec3 dir, float coneRatio, float maxDist) {
    return vec4(1,0,0,0);
}
#endif

// https://www.gamedev.net/forums/topic/695881-voxel-grid-traversal-algorithm/ THANKS!
// P - ray origin position
// V - ray direction
// cone_ratio - using 1 large cone lead to poor results, multiple ones with some sane ratio looks a lot better
// max_dist - maximum acceptable distance for cone to travel (usable for AO F.e.)
// step_mult - in case we want "faster" and even less-precise stepping
vec4 ConeTraceGI(in vec3 P, in vec3 V, in float cone_ratio, in float max_dist, in float step_mult)
{
    float min_voxel_diameter = voxelGrids[voxelGridIndex].scale;
    float min_voxel_diameter_inv = 1.0 / min_voxel_diameter;

    vec4 accum = vec4(0.0f);

    // push out the starting point to avoid self-intersection
    float dist = min_voxel_diameter;

    VoxelGrid voxelGrid = voxelGrids[voxelGridIndex];

    // Marching!
    while (dist <= max_dist && accum.a < 1.0f)
    {
        // Calculate which level to sample
        float sample_diameter = max(min_voxel_diameter, cone_ratio * dist);
        float sample_lod = log2(sample_diameter * min_voxel_diameter_inv);

        // Step
        vec3 sample_pos = P + V * dist;
        dist += sample_diameter * step_mult;

        // Sample from 3D texture (in performance superior to Sparse Voxel Octrees, hence use these)
        #if defined(BINDLESSTEXTURES) && defined(SHADER5)
        vec4 sample_value = voxelFetch(voxelGrid, toSampler(voxelGrid.gridHandle), sample_pos, sample_lod);
        #else
        vec4 sample_value = voxelFetch(voxelGrid, grid, sample_pos, sample_lod);
        #endif

        float a = 1.0 - accum.a;
        accum += sample_value * a;
    }

    return accum;
}

void main(void) {

    VoxelGridArray voxelGridArray;
    voxelGridArray.size = voxelGridCount;
    voxelGridArray.voxelGrids = voxelGrids;

	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	float depth = texture(normalMap, st).w;
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
	vec4 visibility = textureLod(visibilityMap, st, 0);
  	vec3 positionWorld = (inverse(viewMatrix) * vec4(positionView, 1)).xyz;
	//vec4 positionViewPreW = (inverse(projectionMatrix)*vec4(st, depth, 1));
	//positionView = positionViewPreW.xyz / positionViewPreW.w;
    if(!isInsideVoxelGrid(positionWorld, voxelGrids[voxelGridIndex])) {
        discard;
    }

    vec4 colorTransparency = textureLod(diffuseMap, st, 0);
	vec3 color = colorTransparency.xyz;
	float roughness = textureLod(positionMap, st, 0).a;

  	vec4 position_clip_post_w = (projectionMatrix * vec4(positionView,1));
  	position_clip_post_w = position_clip_post_w/position_clip_post_w.w;
	vec4 dir = (inverse(projectionMatrix)) * vec4(position_clip_post_w.xy,1.0,1.0);
	dir.w = 0.0;
	vec3 V = (inverse(viewMatrix) * dir).xyz;
	V = positionView;
	V = -normalize((positionWorld.xyz - eyePosition.xyz).xyz);

    vec4 normalAmbient = textureLod(normalMap, st, 0);
    vec3 normalView = normalAmbient.xyz;
    // skip background
    if (length(normalView) < 0.5f) {
        discard;
    }
	vec3 normalWorld = ((inverse(viewMatrix)) * vec4(normalView,0.0)).xyz;

	float metallic = textureLod(diffuseMap, st, 0).a;

    vec4 motionVecProbeIndices = textureLod(motionMap, st, 0);
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

    float aperture = tan(0.0003474660443456835 + (roughness * (1.3331290497744692 - (roughness * 0.5040552688878546))));

    if(false && !debugVoxels && useVoxelConeTracing) {

#if defined(BINDLESSTEXTURES) && defined(SHADER5)
        vec4 voxelDiffuse = vec4(4.0f)*traceVoxelsDiffuse(voxelGridArray, normalWorld, positionWorld);
#else
        vec4 voxelDiffuse = vec4(4.0f)*traceVoxelsDiffuse(voxelGridArray, normalWorld, positionWorld, grid);
#endif
        vec4 voxelSpecular = vec4(4.0f);//*voxelTraceConeXXX(voxelGridArray, GRID1, positionWorld, normalize(reflect(-V, normalWorld)), aperture, 370);

        vct += boost*(specularColor.rgb*voxelSpecular.rgb + diffuseColor * voxelDiffuse.rgb);

//            const bool useTransparency = false;
//            if(useTransparency) {
//                vec3 sampledValue = voxelTraceCone(voxelGrid, grid, positionWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
//        	compensate missing secondary bounces
//        	sampledValue += 0.1*voxelTraceCone(albedoGrid, gridSize, sceneScale, sceneScale, positionWorld-sceneScale*1*normalWorld, normalize(refract(-V, normalWorld,1-roughness)), 0.1*roughness, 370).rgb;
//                vct = /* small boost factor, figure out why */ 4 * boost * color * sampledValue * (transparency) + vct * opacity;
//            }
    }

    if(useAmbientOcclusion){
        vec4 AOscattering = textureLod(aoScattering, 0.5f*st, 0);
        vct *= clamp(AOscattering.r,0.0,1.0);
    }

    VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];

    if(debugVoxels) {
        vec3 temp = scatter(eyePosition + normalize(positionWorld-eyePosition), eyePosition, voxelGridArray.voxelGrids);
        if(temp.x > 0 || temp.y > 0 || temp.z > 0) {
            vct += temp;
        }
#if defined(BINDLESSTEXTURES) && defined(SHADER5)
        const bool onlySample = false;
#else
        const bool onlySample = true;
#endif
        if(onlySample) {

            #if defined(BINDLESSTEXTURES) && defined(SHADER5)
                vct = voxelFetch(voxelGrid, toSampler(voxelGrid.albedoGridHandle), positionWorld.xyz, 0).rgb;
            #else
//            if(voxelGridIndex == 1) {
                vct = 0.25f*voxelFetch(voxelGrid, albedoGrid, positionWorld.xyz, 0).rgb;
//            }
            #endif
        }
    } else {

        const int sampleCount = 5;
        for (int k = 0; k < sampleCount; k++) {
            const float PI = 3.1415926536;
            vec2 Xi = hammersley2d(k, sampleCount);
            float Phi = 2 * PI * Xi.x;
            float a = 0.5;
            float CosTheta = sqrt((1 - Xi.y) / ((1 + (a*a - 1) * Xi.y)));
            float SinTheta = sqrt(1 - CosTheta * CosTheta);

            vec3 H;
            H.x = SinTheta * cos(Phi);
            H.y = SinTheta * sin(Phi);
            H.z = CosTheta;
            H = hemisphereSample_uniform(Xi.x, Xi.y, normalWorld);

            vct = diffuseColor * ConeTraceGI(positionWorld.xyz, H, 0.5f*aperture, 150.0f, 1.0f).rgb;

        }
        vct += specularColor * ConeTraceGI(positionWorld.xyz, reflect(-V, normalWorld.xyz), aperture, 250.0f, 1.0f).rgb;

//        vct = diffuseColor * ConeTraceGI(positionWorld.xyz, normalWorld.xyz, aperture, 150.0f, 1.0f).rgb
//            + specularColor * ConeTraceGI(positionWorld.xyz, reflect(-V, normalWorld.xyz), aperture, 250.0f, 1.0f).rgb;
    }

    out_AOReflection.rgb = 4.0f*vct;
}
