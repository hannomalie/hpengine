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

	float hasDiffuseMap;
	float hasNormalMap;
	float hasSpecularMap;
};