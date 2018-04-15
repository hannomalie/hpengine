
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

uniform float sceneScale = 1;
uniform float inverseSceneScale = 1;
uniform int gridSize;

uniform float extent = 5f;
//uniform int dimension = 6;
//uniform int dimensionHalf = dimension/2;

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

//include(globals.glsl)

vec4 getViewPosInTextureSpace(vec3 viewPosition) {
	vec4 projectedCoord = projectionMatrix * vec4(viewPosition, 1);
    projectedCoord.xy /= projectedCoord.w;
    projectedCoord.xy = projectedCoord.xy * 0.5 + 0.5;
    return projectedCoord;
}
int to1D( int x, int y, int z ) {
    return (z * 6 * 6) + (y * 6) + x;
}

const float kernel3[3][3] = {{ 1.0/16.0, 2.0/16.0, 1.0/16.0},
				{ 2.0/16.0, 4.0/16.0, 2.0/16.0 },
				{ 1.0/16.0, 2.0/16.0, 1.0/16.0 } };

vec3 getSampleForProbeInPosition(vec3 probePositionGrid, vec3 normalWorld, int dimension, vec3 positionWorld) {
        vec3 probeInGridClamped = clamp(probePositionGrid, vec3(0), vec3(dimension));
        ivec3 probeAsInt = ivec3(probeInGridClamped);
        int probeIndex = to1D(probeAsInt.x, probeAsInt.y, probeAsInt.z);

        const float mipLevel = 3f;

        vec4 probeValue = textureLod(probes, vec4(normalWorld, probeIndex), mipLevel);

        vec3 probeWorld = (probeInGridClamped - dimension/2)*extent;
        float NdotL = 1;//clamp(dot(normalize(probeWorld - positionWorld.xyz), normalWorld.xyz), 0.0f, 1.0f);
        float visibility = 1;//probeValue.a > (distance(positionWorld.xyz, probeWorld))*1000f ? 0.f : 1.f;

        return probeValue.rgb * NdotL * visibility;
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


    const int dimension = 6;
    const int dimensionHalf = dimension/2;
    vec3 positionGridScaled = positionWorld.xyz/extent;
    vec3 positionInGridClamped = clamp(positionGridScaled + vec3(dimensionHalf), vec3(0), vec3(dimension));
    ivec3 positionInGrid = ivec3(positionInGridClamped);
    int probeIndex = to1D(positionInGrid.x,positionInGrid.y,positionInGrid.z);

    const int RADIUS_BASED = 0;
    const int TRILINEAR = 1;
    const int mode = TRILINEAR;
    if(mode == RADIUS_BASED) {
        const float ambient = 0.1f;
        int radius = 1;
        float visibilitySum = 0;


        for(int xOffset = -radius; xOffset < radius; xOffset++) {
            for(int yOffset = -radius; yOffset < radius; yOffset++) {
                for(int zOffset = -radius; zOffset < radius; zOffset++) {

                    ivec3 neighborPositionWorld = ivec3(round(positionWorld.xyz) + extent*vec3(xOffset, yOffset, zOffset));
                    ivec3 neighborPositionInGrid = ivec3(clamp((neighborPositionWorld/extent) + vec3(dimensionHalf), 0, dimension));
                    int probeIndexNeighbor = to1D(neighborPositionInGrid.x, neighborPositionInGrid.y, neighborPositionInGrid.z);
                    vec3 probePosition = neighborPositionWorld;

                    const float maxDist = length(vec3(5f));
                    float actualDist = clamp(distance(vec3(neighborPositionWorld), positionWorld.xyz), 0, maxDist);
                    float interpolator = actualDist/maxDist;

                    const float mipLevel = 4f;
                    vec4 probeValue = textureLod(probes, vec4(normalWorld, probeIndex), mipLevel);
                    float NdotL = 1;//clamp(dot(normalWorld, -vec3(probePosition - positionWorld.xyz)), 0, 1);
                    float shadowMapSample = textureLod(probes, vec4(normalWorld, probeIndex), 0).a*1000f;
                    float visibility = 1;//distance(positionWorld.xyz, probePosition) > shadowMapSample ? ambient : 1;
                    vec3 light = NdotL * probeValue.rgb * visibility;

                    vec4 probeValueNeightbor = textureLod(probes, vec4(normalWorld, probeIndexNeighbor), mipLevel);
                    float NdotLNeighbor = 1;//clamp(dot(normalWorld, -vec3(neighborPosition - positionWorld.xyz)), 0, 1);
                    float shadowMapSampleNeighbor = textureLod(probes, vec4(normalWorld, probeIndexNeighbor), 0).a*1000f;
                    float visibilityNeighbor = 1;//distance(positionWorld.xyz, neighborPosition) > shadowMapSampleNeighbor ? ambient : 1;
                    vec3 lightNeighbor = NdotLNeighbor * probeValueNeightbor.rgb * visibilityNeighbor;

                    out_DiffuseSpecular.rgb += mix(light, lightNeighbor, interpolator);
                    visibilitySum += mix(visibility, visibilityNeighbor, interpolator);


                }
            }
        }
        out_DiffuseSpecular.rgb *= color.rgb;
    } else if(mode == TRILINEAR) {

        const float offset = 1f;
        float x = positionInGridClamped.x;
        float y = positionInGridClamped.y;
        float z = positionInGridClamped.z;

        vec3 c000 = floor(vec3(x,y,z));
        vec3 c100 = floor(vec3(x+offset, y, z));
        vec3 c010 = floor(vec3(x, y, z+offset));
        vec3 c110 = floor(vec3(x+offset, y, z+offset));

        y = y+offset;
        vec3 c001 = floor(vec3(x,y,z));
        vec3 c101 = floor(vec3(x+offset, y, z));
        vec3 c011 = floor(vec3(x, y, z+offset));
        vec3 c111 = floor(vec3(x+offset, y, z+offset));


        vec3 d = (positionInGridClamped.xyz - c000) / (c111 - c000);
        vec3 c00 = getSampleForProbeInPosition(c000,normalWorld, dimension, positionWorld.xyz) * (1 - d.x) + getSampleForProbeInPosition(c100,normalWorld, dimension, positionWorld.xyz)*d.x;
        vec3 c01 = getSampleForProbeInPosition(c001,normalWorld, dimension, positionWorld.xyz) * (1 - d.x) + getSampleForProbeInPosition(c101,normalWorld, dimension, positionWorld.xyz)*d.x;
        vec3 c10 = getSampleForProbeInPosition(c010,normalWorld, dimension, positionWorld.xyz) * (1 - d.x) + getSampleForProbeInPosition(c110,normalWorld, dimension, positionWorld.xyz)*d.x;
        vec3 c11 = getSampleForProbeInPosition(c011,normalWorld, dimension, positionWorld.xyz) * (1 - d.x) + getSampleForProbeInPosition(c111,normalWorld, dimension, positionWorld.xyz)*d.x;

        vec3 c0 = c00 * (1 - d.z) + c10*d.z;
        vec3 c1 = c01 * (1 - d.z) + c11*d.z;

        vec3 c = c0 * (1 - d.y) + c1*d.y;

        out_DiffuseSpecular.rgb += c;

    }

        out_DiffuseSpecular.rgb *= color.rgb;
        out_DiffuseSpecular.rgb += (0.1+normalAmbient.a)*color.rgb;
//        out_DiffuseSpecular.rgb += 0.25*color.rgb;
//        out_DiffuseSpecular.rgb /= interpolatorSum;

}
