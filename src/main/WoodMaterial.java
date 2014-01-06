package main;


public class WoodMaterial extends Material {

	public WoodMaterial() {
		setup();
	}
	
	public void setup() {
		String texture = "wood";
		texIds[0] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_diffuse.png", ForwardRenderer.getMaterialProgramId(), "diffuseMap", 0);
		texIds[1] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_normal.png", ForwardRenderer.getMaterialProgramId(), "normalMap", 1);
		texIds[2] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_specular.png", ForwardRenderer.getMaterialProgramId(), "specularMap", 2);
		texIds[3] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_occlusion.png", ForwardRenderer.getMaterialProgramId(), "occlusionMap", 3);
		texIds[4] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_height.png", ForwardRenderer.getMaterialProgramId(), "heightMap", 4);
		
		ForwardRenderer.exitOnGLError("setupTexture");
	}
}
