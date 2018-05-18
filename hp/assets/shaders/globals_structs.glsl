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
	float materialtype;
	int hasDiffuseMap;
	int hasNormalMap;

	int hasSpecularMap;
	int hasHeightMap;
	int hasOcclusionMap;
	int hasRoughnessMap;

    double handleDiffuse;
    double handleNormal;
    double handleSpecular;
    double handleHeight;

    double handleOcclusion;
    double handleRoughness;
    int placeHolder0;
    int placeHolder1;
};

struct PointLight {
	double positionX;
	double positionY;
	double positionZ;
	double radius;

	double colorR;
	double colorG;
	double colorB;
	double xxx;
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