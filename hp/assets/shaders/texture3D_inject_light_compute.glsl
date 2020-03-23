#define WORK_GROUP_SIZE 8

layout(local_size_x = WORK_GROUP_SIZE, local_size_y = WORK_GROUP_SIZE, local_size_z = WORK_GROUP_SIZE) in;
layout(binding=0) writeonly uniform image3D targetVoxelGrid;
layout(binding=6) uniform sampler2D shadowMap;

#if defined(BINDLESSTEXTURES) && defined(SHADER5)
#else
layout(binding=1) uniform sampler3D albedoGrid;
layout(binding=2) uniform sampler3D normalGrid;
layout(binding=3) uniform isampler3D indexGrid;
#endif
uniform mat4 shadowMatrix;

uniform int bounces = 1;
//include(globals_structs.glsl)
//include(globals.glsl)
//include(global_lighting.glsl)

layout(std430, binding=1) buffer _materials {
    Material materials[100];
};
uniform int pointLightCount;
layout(std430, binding=2) buffer _lights {
	PointLight pointLights[100];
};
layout(std430, binding=3) buffer _directionalLightState {
	DirectionalLightState directionalLight;
};
layout(std430, binding=4) buffer _entities {
    Entity entities[2000];
};
layout(std430, binding=5) buffer _voxelGrids {
    VoxelGrid[10] voxelGrids;
};
uniform int voxelGridIndex = 0;
uniform int voxelGridCount = 0;

uniform int nodeCount = 1;
layout(std430, binding=6) buffer _bvh {
    BvhNode nodes[];
};

void main(void) {

    VoxelGrid voxelGrid = voxelGrids[voxelGridIndex];
    float sceneScale = voxelGrid.scale;
    float inverseSceneScale = 1.0f/sceneScale;

#if defined(BINDLESSTEXTURES) && defined(SHADER5)
    sampler3D albedoGrid = sampler3D(voxelGrid.albedoGridHandle);
    sampler3D normalGrid = sampler3D(voxelGrid.normalGridHandle);
#endif

    ivec3 storePos = ivec3(gl_GlobalInvocationID.xyz);
    ivec3 workGroup = ivec3(gl_WorkGroupID);
    ivec3 workGroupSize = ivec3(gl_WorkGroupSize.xyz);
    ivec3 localIndex = ivec3(gl_LocalInvocationID.xyz);

    vec4 sourceValue = vec4(0);
    float weightSum = 0;

    vec3 positionWorld = gridToWorldPosition(voxelGrid, storePos);
    vec3 samplePositionNormalized = vec3(positionWorld)/vec3(voxelGrid.resolution)+vec3(0.5f);

    vec4 color = voxelFetch(voxelGrid, albedoGrid, positionWorld, 0.0f);
    vec4 normalStatic = voxelFetch(voxelGrid, normalGrid, positionWorld, 0.0f);
    int entityIndex = voxelFetchI(voxelGrid, indexGrid, positionWorld, 0.0f).r;

    Entity entity = entities[entityIndex];
    Material material = materials[entity.materialIndex];

    vec3 g_normal = normalize(normalStatic.rgb);
    vec3 g_pos = positionWorld;
    float opacity = color.a;
    float isStatic = entity.isStatic;

    //second bounce?
    vec3 ambientAmount = vec3(0.0f);
    float dynamicAdjust = 0;//.015f;
    vec3 voxelColor = color.rgb;
    float emissive = 4.0f * material.ambient;// TODO: Readd emissive materials like before with normalStatic.a;
    vec3 voxelColorAmbient = (vec3(ambientAmount)+float(emissive))*voxelColor;

    vec4 positionShadow = (shadowMatrix * vec4(positionWorld.xyz, 1));
    positionShadow.xyz /= positionShadow.w;
    float depthInLightSpace = positionShadow.z;
    positionShadow.xyz = positionShadow.xyz * 0.5 + 0.5;
//    float visibility = clamp(getVisibility(depthInLightSpace, positionShadow), 0.0f, 1.0f).r;

    #if BINDLESS_TEXTURES
    float visibility = getVisibility(positionWorld, directionalLight);
    #else
    float visibility = getVisibility(positionWorld.xyz, directionalLight, shadowMap);
    #endif

    vec3 lightDirectionTemp = -directionalLight.direction;
    float NdotL = max(0.1, clamp(dot(g_normal, normalize(lightDirectionTemp)), 0.0f, 1.0f));

    vec3 finalVoxelColor = voxelColorAmbient+(NdotL*vec4(directionalLight.color,1)*visibility*vec4(voxelColor,1)).rgb;

//    for(int i = 0; i < pointLightCount; i++) {
//        PointLight pointLight = pointLights[i];
//        vec3 lightPosition = pointLight.position;
//        vec3 lightDiffuse = pointLight.color;
//        vec3 lightDirection = normalize(vec4(lightPosition - positionWorld, 0)).xyz;
//        float attenuation = calculateAttenuation(length(lightPosition - positionWorld.xyz), float(pointLight.radius));
//
//        finalVoxelColor += attenuation*clamp(dot(lightDirection, g_normal), 0, 1) * lightDiffuse * voxelColor;
//    }

    int nextIndex = 0;

    uint maxIterations = 350;
    while(nextIndex < nodeCount && maxIterations > 0) {
        BvhNode node = nodes[nextIndex];
        int hitPointer = nextIndex += 1;
        bool isLeaf = node.missPointer == hitPointer;
        if(isInsideSphere(positionWorld.xyz, node.positionRadius)) {
            if(isLeaf) {
                PointLight pointLight = pointLights[node.lightIndex];
                vec3 lightPosition = pointLight.position;
                vec3 lightDiffuse = pointLight.color;
                vec3 lightDirection = normalize(vec4(lightPosition - positionWorld, 0)).xyz;
                float attenuation = calculateAttenuation(length(lightPosition - positionWorld.xyz), float(pointLight.radius));

                finalVoxelColor += attenuation*clamp(dot(lightDirection, g_normal), 0, 1) * lightDiffuse * voxelColor;
            }
            nextIndex = hitPointer;
        } else {
            nextIndex = node.missPointer;
        }

        maxIterations--;
    }

    imageStore(targetVoxelGrid, storePos, vec4(finalVoxelColor, opacity));
//    if(entityIndex < 15)
//    {
//        imageStore(targetVoxelGrid, storePos, vec4(1.0f, 1.0f, 0.0f, opacity));
//    }
}
