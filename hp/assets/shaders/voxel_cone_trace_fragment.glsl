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
    VoxelGridArray voxelGridArray;
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

//include(globals_structs.glsl)
//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec3 scatter(vec3 worldPos, vec3 startPosition, VoxelGridArray voxelGridArray) {
	vec3 rayVector = worldPos.xyz - startPosition;

	vec3 rayDirection = normalize(rayVector);

	vec3 currentPosition = startPosition;

	vec3 lit = vec3(0,0,0);
	vec3 lit2 = vec3(0,0,0);
	vec3 accumAlbedo = vec3(0,0,0);
	vec3 normalValue = vec3(0,0,0);
	vec3 isStaticValue = vec3(0,0,0);
	float accumAlpha = 0;

	float mipLevel = 0f;
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
        float step_accumAlpha = 0;

		float minScale = 1000000.0;
        for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
            VoxelGrid voxelGrid = voxelGridArray.voxelGrids[voxelGridIndex];

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
            vec4 sampledLit2Value = voxelFetch(voxelGrid, toSampler(voxelGrid.grid2Handle), currentPosition, mipLevel);
            step_normalValue.rgb = sampledNormalValue.rgb;
            step_lit = sampledLitValue.rgb * sampledLitValue.a;
            step_lit2 = sampledLit2Value.rgb * sampledLitValue.a;
            step_isStaticValue = vec3(normalValue.b);
            float alpha = 1 - sampledValue.a;
            step_accumAlbedo = sampledValue.rgb * alpha;
            step_accumAlpha = sampledValue.a * alpha;
        }


        lit += step_lit;
        lit2 += step_lit2;
        accumAlbedo += step_accumAlbedo;
        normalValue = step_normalValue.rgb;
        isStaticValue = step_isStaticValue;
        accumAlpha += step_accumAlpha;

        currentPosition += step;
	}
	accumAlbedo *= accumAlpha;
	return lit2.rgb;
}

vec4 voxelTraceConeXXX(VoxelGridArray voxelGridArray, int gridIndex, vec3 origin, vec3 dir, float coneRatio, float maxDist) {

    vec4 accum = vec4(0.0);
    float alpha = 0;
    float dist = 0;
    vec3 samplePos = origin;// + dir;

    while (dist <= maxDist && alpha < 1.0)
    {
        float minScale = 100000.0;
        int canditateIndex = -1;
        VoxelGrid voxelGrid;
        for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
            VoxelGrid candidate = voxelGridArray.voxelGrids[voxelGridIndex];
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
            } else if(gridIndex == GRID1) {
                grid = toSampler(voxelGrid.gridHandle);
            } else {
                grid = toSampler(voxelGrid.grid2Handle);
            }
            sampler3D grid2 = toSampler(voxelGrid.grid2Handle);
            int gridSize = voxelGrid.resolution;

            float sampleLOD = log2(diameter * minVoxelDiameterInv);
            vec4 sampleValue = voxelFetch(voxelGrid, grid, samplePos, sampleLOD);
            vec4 sampleValue2 = voxelFetch(voxelGrid, grid2, samplePos, sampleLOD);

            accum.rgb += max(sampleValue.rgb, sampleValue2.rgb);
            float a = 1 - alpha;
            alpha += a * max(sampleValue.a, sampleValue2.a);
        }

        dist += increment;
        samplePos = origin + dir * dist;
        increment *= 1.25f;
    }
	return vec4(accum.rgb, alpha);
}

vec4 voxelTraceConeXXXTwoGrids(VoxelGridArray voxelGridArray, vec3 origin, vec3 dir, float coneRatio, float maxDist) {

     vec4 accum = vec4(0.0);
     float alpha = 0;
     float dist = 0;
     vec3 samplePos = origin;// + dir;

     while (dist <= maxDist && alpha < 1.0)
     {
         float minScale = 100000.0;
         int canditateIndex = -1;
         VoxelGrid voxelGrid;
         for(int voxelGridIndex = 0; voxelGridIndex < voxelGridArray.size; voxelGridIndex++) {
             VoxelGrid candidate = voxelGridArray.voxelGrids[voxelGridIndex];
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
             sampler3D grid = toSampler(voxelGrid.gridHandle);
             sampler3D grid2 = toSampler(voxelGrid.grid2Handle);
             int gridSize = voxelGrid.resolution;

             float sampleLOD = log2(diameter * minVoxelDiameterInv);
             vec4 sampleValue = voxelFetch(voxelGrid, grid, samplePos, sampleLOD);
             sampleValue += voxelFetch(voxelGrid, grid2, samplePos, sampleLOD);
             vec4 albedoValue;// = voxelFetch(voxelGrid, toSampler(voxelGrid.albedoGridHandle), samplePos, sampleLOD);

             accum.rgb += max(sampleValue.rgb, 0.015*albedoValue.rgb);
             float a = 1 - alpha;
             alpha += a * max(sampleValue.a, albedoValue.a);
         }

         dist += increment;
         samplePos = origin + dir * dist;
         increment *= 1.25f;
     }
 	return vec4(accum.rgb, alpha);
 }
void main(void) {
	vec2 st;
	st.s = gl_FragCoord.x / screenWidth;
  	st.t = gl_FragCoord.y / screenHeight;

	float depth = texture(normalMap, st).w;
	vec3 positionView = textureLod(positionMap, st, 0).xyz;
	vec4 visibility = textureLod(visibilityMap, st, 0);
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
	if (int(visibility.b) == skyBoxMaterialIndex) {
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
        vec4 voxelDiffuse = 4*traceVoxelsDiffuse(voxelGridArray, normalWorld, positionWorld);
        float aperture = 0.1*roughness;//tan(0.0003474660443456835 + (roughness * (1.3331290497744692 - (roughness * 0.5040552688878546))));
        vec4 voxelSpecular = 4*voxelTraceConeXXX(voxelGridArray, GRID2, positionWorld, normalize(reflect(-V, normalWorld)), aperture, 370);

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
        vec4 AOscattering = textureLod(aoScattering, st, 0);
        vct *= clamp(AOscattering.r,0.0,1.0);
    }

    if(debugVoxels) {
        vec3 temp = scatter(eyePosition + normalize(positionWorld-eyePosition), eyePosition, voxelGridArray);
        if(temp.x > 0 || temp.y > 0 || temp.z > 0) {
            vct += temp;
        }
        const bool onlySample = false;
        if(onlySample) {
            VoxelGrid voxelGrid = voxelGridArray.voxelGrids[0];
            vct = voxelFetch(voxelGrid, toSampler(voxelGrid.grid2Handle), positionWorld.xyz, 0).rgb;
        }
    }
    out_DiffuseSpecular.rgb = vct;
}
