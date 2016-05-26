
layout(binding=0) uniform sampler2D positionMap;
layout(binding=1) uniform sampler2D normalMap;
layout(binding=2) uniform sampler2D diffuseMap;
layout(binding=3) uniform sampler2D motionMap;
layout(binding=4) uniform samplerCube environmentMap;

layout(binding=6) uniform sampler2D shadowMap; // momentum1, momentum2, normal
layout(binding=7) uniform sampler2D visibilityMap;
layout(binding=8) uniform samplerCubeArray probes;

layout(binding=13) uniform sampler3D grid;

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

vec3 hemisphereSample_uniform(float u, float v, vec3 N) {
    const float PI = 3.1415926536;
     float phi = u * 2.0 * PI;
     float cosTheta = 1.0 - v;
     float sinTheta = sqrt(1.0 - cosTheta * cosTheta);
     vec3 result = vec3(cos(phi) * sinTheta, sin(phi) * sinTheta, cosTheta);

	vec3 UpVector = abs(N.z) < 0.999 ? vec3(0,0,1) : vec3(1,0,0);
	vec3 TangentX = normalize( cross( UpVector, N ) );
	vec3 TangentY = cross( N, TangentX );
	 // Tangent to world space
	 result = TangentX * result.x + TangentY * result.y + N * result.z;
     //mat3 transform = createOrthonormalBasis(N);
	 //result = (transform) * result;

     return result;
}

float linstep(float low, float high, float v){
    return clamp((v-low)/(high-low), 0.0, 1.0);
}

float radicalInverse_VdC(uint bits) {
     bits = (bits << 16u) | (bits >> 16u);
     bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
     bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
     bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
     bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);

     return float(bits) * 2.3283064365386963e-10; // / 0x100000000
}
vec2 hammersley2d(uint i, int N) {
	return vec2(float(i)/float(N), radicalInverse_VdC(i));
}

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}

vec4 voxelFetch(vec3 positionWorld, float loD) {
    const int gridSizeHalf = gridSize/2;

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    if(any(greaterThan(positionGridScaled, vec3(gridSizeHalf))) ||
       any(lessThan(positionGridScaled, -vec3(gridSizeHalf)))) {

       return vec4(0);
    }

    int level = int(loD);
    vec3 positionAdjust = vec3(gridSize/pow(2, level+1));
    float positionScaleFactor = pow(2, level);

    vec3 samplePositionNormalized = vec3(positionGridScaled)/vec3(gridSize)+vec3(0.5);

    return textureLod(grid, samplePositionNormalized, level);
}

vec4 voxelTraceCone(float minVoxelDiameter, vec3 origin, vec3 dir, float coneRatio, float maxDist) {
	float minVoxelDiameterInv = 1.0/minVoxelDiameter;
	vec3 samplePos = origin;
	vec4 accum = vec4(0.0);
	float minDiameter = minVoxelDiameter;
	float startDist = minDiameter;
	float dist = startDist;
	vec4 ambientLightColor = vec4(0.);
	vec4 fadeCol = ambientLightColor*vec4(0, 0, 0, 0.2);
	while (dist <= maxDist && accum.w < .9)
	{
		float sampleDiameter = max(minDiameter, coneRatio * dist);
		float sampleLOD = log2(sampleDiameter * minVoxelDiameterInv);
		vec3 samplePos = origin + dir * dist;
//		sampleLOD = 3f;
		vec4 sampleValue = voxelFetch(samplePos-dir, sampleLOD);
		sampleValue = mix(sampleValue,fadeCol, clamp(dist/maxDist-0.25, 0.0, 1.0));
		float sampleWeight = (1.0 - accum.w);
		accum += sampleValue * sampleWeight;
		dist += sampleDiameter;
	}
	return accum;
}


vec4 specConeTrace(vec3 o, vec3 dir, float coneRatio, float maxDist)
{
//    float sceneScale = 2;
//     float inverseSceneScale = 1f/sceneScale;
//     o = vec3(inverseSceneScale) * o;
    float voxDim = 256;
	vec3 samplePos = o;
	vec4 accum = vec4(0.0);
	float minDiam = 1.0/voxDim;
	float startDist = 2*minDiam;

	float dist = startDist;
	while(dist <= maxDist && accum.w < 1.0)
	{
		float sampleDiam = max(minDiam, coneRatio*dist);
		float sampleLOD = log2(sampleDiam*voxDim);
		vec3 samplePos = o + dir*dist;
//		sampleLOD = 1.5;
		vec4 sampleVal = voxelFetch(samplePos-dir, sampleLOD);//sampleSpecVox(samplePos, -d, sampleLOD);

		float sampleWt = (1.0 - accum.w);
		accum += sampleVal * sampleWt;

		dist += sampleDiam;
	}

	accum.xyz *= 2.0;

	return accum;
}

vec4 traceVoxels(vec3 worldPos, vec3 startPosition, float lod) {
	const int NB_STEPS = 30;

	vec3 rayVector = worldPos.xyz - startPosition;

	float rayLength = length(rayVector);
	vec3 rayDirection = rayVector / rayLength;

	float stepLength = rayLength / NB_STEPS;

	vec3 step = rayDirection * stepLength;

	vec3 currentPosition = startPosition;

	vec4 accumFog = vec4(0);

    int stepCount = 0;
	for (int i = 0; i < NB_STEPS; i++) {
	    stepCount++;
	    if(accumFog.a >= 0.99f) { break; }
        accumFog += voxelFetch(currentPosition, lod);
		currentPosition += step;
	}
	accumFog /= stepCount;
	return accumFog;
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

	vec3 color = texture2D(diffuseMap, st).xyz;
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

	float metallic = texture2D(diffuseMap, st).a;
	float glossiness = (1-roughness);
	vec3 maxSpecular = mix(vec3(0.2,0.2,0.2), color, metallic);
	vec3 specularColor = mix(vec3(0.2, 0.2, 0.2), maxSpecular, glossiness);
  	vec3 diffuseColor = mix(color, vec3(0,0,0), clamp(metallic, 0, 1));

    vec3 positionGridScaled = inverseSceneScale*positionWorld;
    float gridSizeHalf = float(gridSize/2);
    const bool useVoxelConeTracing = true;
    vec3 vct;
    if(useVoxelConeTracing && positionGridScaled.x > -gridSizeHalf && positionGridScaled.y > -gridSizeHalf && positionGridScaled.z > -gridSizeHalf &&
        positionGridScaled.x < gridSizeHalf && positionGridScaled.y < gridSizeHalf && positionGridScaled.z < gridSizeHalf) {

//        out_color.rgb = voxelFetch(ivec3(positionWorld), 0).rgb;
//        out_color.rgb = 100*traceVoxels(positionWorld, camPosition, 3).rgb;

        //vec4 voxelTraceCone(float minVoxelDiameter, vec3 origin, vec3 dir, float coneRatio, float maxDist)
        vec4 voxelSpecular = voxelTraceCone(1, positionWorld, normalize(reflect(-V, normalWorld)), 0.1*roughness, 370); // 0.05
        vec4 voxelDiffuse;// = 8f*voxelTraceCone(2, positionWorld, normalize(normalWorld), 5, 100);

        const int SAMPLE_COUNT = 8;
        for (int k = 0; k < SAMPLE_COUNT; k++) {
            const float PI = 3.1415926536;
            vec2 Xi = hammersley2d(k, SAMPLE_COUNT);
            float Phi = 2 * PI * Xi.x;
            float a = 1;//roughness;
            float CosTheta = sqrt( (1 - Xi.y) / (( 1 + (a*a - 1) * Xi.y )) );
            float SinTheta = sqrt( 1 - CosTheta * CosTheta );

            vec3 H;
            H.x = SinTheta * cos( Phi );
            H.y = SinTheta * sin( Phi );
            H.z = CosTheta;
	        H = hemisphereSample_uniform(Xi.x, Xi.y, normalWorld);

            float dotProd = clamp(dot(normalWorld, H),0,1);
            voxelDiffuse += vec4(dotProd) * voxelTraceCone(6, positionWorld, normalize(H), 6, 150);
        }

//https://github.com/thefranke/dirtchamber/blob/master/shader/vct_tools.hlsl
//    vec3 diffdir = normalize(normalWorld.zxy);
//    vec3 crossdir = cross(normalWorld.xyz, diffdir);
//    vec3 crossdir2 = cross(normalWorld.xyz, crossdir);
//
//    // jitter cones
//    float j = 1.0 + (fract(sin(dot(st, vec2(12.9898, 78.233))) * 43758.5453)) * 0.2;
//
//    vec3 directions[9] =
//    {
//        normalWorld,
//        normalize(crossdir   * j + normalWorld),
//        normalize(-crossdir  * j + normalWorld),
//        normalize(crossdir2  * j + normalWorld),
//        normalize(-crossdir2 * j + normalWorld),
//        normalize((crossdir + crossdir2)  * j + normalWorld),
//        normalize((crossdir - crossdir2)  * j + normalWorld),
//        normalize((-crossdir + crossdir2) * j + normalWorld),
//        normalize((-crossdir - crossdir2) * j + normalWorld),
//    };
//
//    float diff_angle = 0.6f;
//
//    vec4 diffuse = vec4(0, 0, 0, 0);
//
//    for (uint d = 0; d < 9; ++d)
//    {
//        vec3 D = directions[d];
//
//        float NdotL = clamp(dot(normalize(normalWorld), normalize(D)), 0, 1);
//
//        float minDiameter = 1.f;
//        voxelDiffuse += voxelTraceCone(minDiameter, positionWorld, normalize(D), 0.5, 50) * NdotL;
//    }
//
//        out_color.rgb += specularColor.rgb*voxelSpecular.rgb * (1-roughness) + color*voxelDiffuse.rgb * (1 - (1-roughness));
        vct += 8*(specularColor.rgb*voxelSpecular.rgb + color*voxelDiffuse.rgb);
    }

    out_DiffuseSpecular.rgb = vct;

}
