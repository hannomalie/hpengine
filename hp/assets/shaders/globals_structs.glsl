struct Material {
	double diffuseR;
	double diffuseG;
	double diffuseB;
	double metallic;

	double roughness;
	double ambient;
	double parallaxBias;
	double parallaxScale;

	double transparency;
	double materialtype;
	double hasDiffuseMap;
	double hasNormalMap;

	double hasSpecularMap;
	double hasHeightMap;
	double hasOcclusionMap;
	double hasRoughnessMap;
};

struct PointLight {
	double positionX;
	double positionY;
	double positionZ;
	double radius;
	double colorR;
	double colorG;
	double colorB;
};

struct Entity {
    dmat4 modelMatrix;
    double isSelected;
    double materialIndex;
    double bla;
    double x;
};