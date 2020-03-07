struct Material {
    vec3 diffuse;
    float metallic;

	float roughness;
	float ambient;
	float parallaxBias;
	float parallaxScale;

	float transparency;
	int materialtype;
	int transparencyType;
	int environmentMapId;

    uvec2 handleDiffuse;
    uvec2 handleNormal;

    uvec2 handleSpecular;
    uvec2 handleHeight;

    uvec2 handleOcclusion;
    uvec2 handleRoughness;

};

struct PointLight {
    vec3 position;
	float radius;

    vec3 color;
    float xxx;
};

struct AreaLight {
    mat4 modelMatrix;

    vec3 color;
    float xxx;

    float width;
    float height;
    float range;
    float yyy;
};

struct Entity {
    mat4 modelMatrix;

    int isSelected;
    int materialIndex;
    int isStatic;
    int entityIndex;

    int entityIndexWithoutMeshIndex; //TODO: Rename this properly
    int meshIndex;
    int baseVertex;
    int baseJointIndex;

    int animationFrame0;
    int animationFrame1;
    int animationFrame2;
    int animationFrame3;

    int invertTexcoordY;
    int visible;
    int b;
    int c;

    vec4 min;
    vec4 max;
};

struct DrawCommand {
    int  count;
    int  instanceCount;
    int  firstIndex;
    int  baseVertex;
    int  baseInstance;
//    uint  baseIndex;
};

struct AmbientCube {
    vec3 position;
    float filler;

    double handle;
    double distanceMapHandle;
};

struct VoxelGrid {
    int albedoGrid;
    int normalGrid;
    int grid;
    int indexGrid;

    int resolution;
    int resolutionHalf;
    int dummy2;
    int dummy3;

    mat4 projectionMatrix;

    vec3 position;
    float scale;

    uvec2 albedoGridHandle;
    uvec2 normalGridHandle;

    uvec2 gridHandle;
    uvec2 indexGridHandle;
};

struct VoxelGridArray {
    int size;
    int dummy0;
    int dummy1;
    int dummy2;
	VoxelGrid voxelGrids[10];
};

struct DirectionalLightState {
    mat4 viewMatrix;
    mat4 projectionMatrix;
    mat4 viewProjectionMatrix;

    vec3 color;
    float dummy;

    vec3 direction;
    float scatterFactor;


    uvec2 shadowMapHandle;
    int shadowMapId;
    float dummy0;
};

struct VertexShaderFlatOutput {
    int entityBufferIndex;
    int entityIndex;
    Entity entity;
    int materialIndex;
    Material material;
    mat3 TBN;
};

struct VertexShaderOutput {
    vec4 color;
    vec2 texCoord;
    vec3 normalVec;
    vec3 normal_model;
    vec3 normal_world;
    vec3 normal_view;
    vec4 position_clip;
    vec4 position_clip_last;
    vec4 position_clip_uv;
    vec4 position_world;
};

struct VertexPacked {
    vec4 position;
    vec4 texCoord;
    vec4 normal;
    vec4 dummy;
};
struct VertexAnimatedPacked {
    vec4 position;
    vec4 texCoord;
    vec4 normal;
    vec4 weights;

    ivec4 jointIndices;
    vec4 dummy;
    vec4 dummy1;
    vec4 dummy2;
};
struct BvhNode {
    vec4 positionRadius;
    int missPointer;
    int lightIndex;
    int dummy0;
    int dummy1;
};