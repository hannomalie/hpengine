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

    uvec2 handleDisplacement;
    uvec2 handleRoughness;

    vec2 uvScale;
    float lodFactor;
    int worldSpaceTexCoords;

    float diffuseMipmapBias;
    int diffuseMapIndex;
    float dummy1;
    float dummy2;
};

struct PointLight {
    vec3 position;
	float radius;

    vec3 color;
    int shadow;
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
    int staticShadowMapId;

    uvec2 staticShadowMapHandle;
    float dummy0;
    float dummy1;
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
    int entityIndex; // TODO: this is meshBufferIndex

    int entityIndexWithoutMeshIndex; //TODO: Rename this properly, it's entityIndex really
    int meshIndex;
    int baseVertex;
    int baseJointIndex;

    int animationFrame0;
    int animationFrame1;
    int animationFrame2;
    int animationFrame3;

    int invertTexcoordY;
    int visible;
    int probeIndex;
    int c;

    vec3 min;
    int boundingVolumeType;
    vec3 max;
    int d;
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

#define MAX_VOXELGRIDS 10
struct VoxelGridArray {
    int size;
    int dummy0;
    int dummy1;
    int dummy2;
	VoxelGrid voxelGrids[MAX_VOXELGRIDS];
};

struct VertexShaderFlatOutput {
    int entityBufferIndex;
    int entityIndex;
    Entity entity;
    int materialIndex;
    Material material;
    mat3 TBN;
    int vertexIndex;
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
    vec3 barycentrics;
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