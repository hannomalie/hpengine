vec3 PCF(sampler2D sampler, vec2 texCoords, float referenceDepth, float inBlurDistance) {
	vec3 result = vec3(0,0,0);
	float blurDistance = clamp(inBlurDistance, 0.0, 0.002);
	const int N = 32;
	const float bias = 0.001;
	for (int i = 0; i < N; i++) {
		result += (texture(sampler, texCoords + (hammersley2d(i, N)-0.5)/100).x > referenceDepth - bias ? 1 : 0);
	}
	return result/N;
}

float maxDepth(sampler2D sampler, vec2 texCoords, float inBlurDistance) {
	float blurDistance = clamp(inBlurDistance, 0.0, 0.0025);

	float result = texture(sampler, texCoords + vec2(-blurDistance, -blurDistance)).r;
	result = max(result, texture(sampler, texCoords + vec2(0, -blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, -blurDistance)).r);

	result = max(result, texture(sampler, texCoords + vec2(-blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(0, 0)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, 0)).r);

	result = max(result, texture(sampler, texCoords + vec2(-blurDistance, blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(0, -blurDistance)).r);
	result = max(result, texture(sampler, texCoords + vec2(blurDistance, blurDistance)).r);

	return result;
}

vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW, DirectionalLightState light, sampler2D shadowMap)
{
    if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
        float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
        return vec3(0,0,0);
    }
    if(USE_PCF) {
        return PCF(shadowMap, ShadowCoordPostW.xy, dist, 0.002);
    }


    // We retrive the two moments previously stored (depth and depth*depth)
    vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 2);
    vec2 moments = shadowMapSample.rg;
    vec2 momentsUnblurred = moments;

    const bool AVOID_LIGHT_BLEEDING = false;
    if(AVOID_LIGHT_BLEEDING) {
        float envelopeMaxDepth = maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0025);
        envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0017);
        envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.00125);
        envelopeMaxDepth /= 3;
        if(envelopeMaxDepth < dist - 0.005) { return vec3(0,0,0); }
    }

    moments = blur(shadowMap, ShadowCoordPostW.xy, 0.0125, 1).rg;
    //moments += blur(shadowMap, ShadowCoordPostW.xy, 0.0017).rg;
    //moments += blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;
    //moments /= 3;

    // Surface is fully lit. as the current fragment is before the light occluder
    if (dist <= moments.x) {
        //return vec3(1.0,1.0,1.0);
    }

    // The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
    // How likely this pixel is to be lit (p_max)
    float variance = moments.y - (moments.x*moments.x);
    variance = max(variance,0.0005);

    float d = dist - moments.x;
    //float p_max = (variance / (variance + d*d));
    // thanks, for light bleeding reduction, FOOGYWOO! http://dontnormalize.me/
    float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));

    p_max = smoothstep(0.1, 1.0, p_max);

    float darknessFactor = 420.0;
    p_max = clamp(exp(darknessFactor * (moments.x - dist)), 0.0, 1.0);

    //p_max = blurESM(shadowMap, ShadowCoordPostW.xy, dist, 0.002);

    return vec3(p_max,p_max,p_max);
}
float getVisibility(vec3 positionWorld, DirectionalLightState light, sampler2D shadowMap) {

    mat4 shadowMatrix = light.viewProjectionMatrix;

    float visibility = 1.0;
    vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
    positionShadow.xyz /= positionShadow.w;
    float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
    visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow, light, shadowMap), 0, 1).r;
    return visibility;
}

#ifdef BINDLESSTEXTURES
vec3 chebyshevUpperBound(float dist, vec4 ShadowCoordPostW, DirectionalLightState light)
{
	if(uint64_t(light.shadowMapHandle) > 0) {
        sampler2D shadowMap = sampler2D(light.shadowMapHandle);

        if (ShadowCoordPostW.x < 0 || ShadowCoordPostW.x > 1 || ShadowCoordPostW.y < 0 || ShadowCoordPostW.y > 1) {
            float fadeOut = max(abs(ShadowCoordPostW.x), abs(ShadowCoordPostW.y)) - 1;
            return vec3(0,0,0);
        }
        if(USE_PCF) {
            return PCF(shadowMap, ShadowCoordPostW.xy, dist, 0.002);
        }


        // We retrive the two moments previously stored (depth and depth*depth)
        vec4 shadowMapSample = textureLod(shadowMap,ShadowCoordPostW.xy, 2);
        vec2 moments = shadowMapSample.rg;
        vec2 momentsUnblurred = moments;

        const bool AVOID_LIGHT_BLEEDING = false;
        if(AVOID_LIGHT_BLEEDING) {
            float envelopeMaxDepth = maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0025);
            envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.0017);
            envelopeMaxDepth += maxDepth(shadowMap, ShadowCoordPostW.xy, 0.00125);
            envelopeMaxDepth /= 3;
            if(envelopeMaxDepth < dist - 0.005) { return vec3(0,0,0); }
        }

        moments = blur(shadowMap, ShadowCoordPostW.xy, 0.0125, 1).rg;
        //moments += blur(shadowMap, ShadowCoordPostW.xy, 0.0017).rg;
        //moments += blur(shadowMap, ShadowCoordPostW.xy, 0.00125).rg;
        //moments /= 3;

        // Surface is fully lit. as the current fragment is before the light occluder
        if (dist <= moments.x) {
            //return vec3(1.0,1.0,1.0);
        }

        // The fragment is either in shadow or penumbra. We now use chebyshev's upperBound to check
        // How likely this pixel is to be lit (p_max)
        float variance = moments.y - (moments.x*moments.x);
        variance = max(variance,0.0005);

        float d = dist - moments.x;
        //float p_max = (variance / (variance + d*d));
        // thanks, for light bleeding reduction, FOOGYWOO! http://dontnormalize.me/
        float p_max = smoothstep(0.20, 1.0, variance / (variance + d*d));

        p_max = smoothstep(0.1, 1.0, p_max);

        float darknessFactor = 420.0;
        p_max = clamp(exp(darknessFactor * (moments.x - dist)), 0.0, 1.0);

        //p_max = blurESM(shadowMap, ShadowCoordPostW.xy, dist, 0.002);

        return vec3(p_max,p_max,p_max);
    } else {
        return vec3(1);
    }
}
float getVisibility(vec3 positionWorld, DirectionalLightState light) {

    mat4 shadowMatrix = light.viewProjectionMatrix;

    float visibility = 1.0;
    vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
    positionShadow.xyz /= positionShadow.w;
    float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
    visibility = clamp(chebyshevUpperBound(depthInLightSpace, positionShadow, light), 0, 1).r;
    return visibility;
}
#endif
