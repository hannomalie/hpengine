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

	double handleDiffuse;
    double handleNormal;
    double handleSpecular;
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