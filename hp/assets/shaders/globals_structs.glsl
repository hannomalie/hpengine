struct Material {
	float diffuseR;
	float diffuseG;
	float diffuseB;
	float metallic;

	float roughness;
	float ambient;
	float parallaxBias;
	float parallaxScale;

	float transparency;
	int materialtype;
	int transparencyType;
	float dummy1;

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
    int grid2;

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
    uvec2 grid2Handle;
};

struct VoxelGridArray {
    int size;
    int dummy0;
    int dummy1;
    int dummy2;
	VoxelGrid voxelGrids[10];
};