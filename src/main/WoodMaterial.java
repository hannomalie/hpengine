package main;


public class WoodMaterial extends Material {

	public WoodMaterial(ForwardRenderer renderer) {
		super(renderer);
	}
	
	public void setup(ForwardRenderer renderer) {
		String texture = "wood";
		texIds[0] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_diffuse.png");
		texIds[1] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_normal.png");
		texIds[2] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_specular.png");
		texIds[3] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_occlusion.png");
		texIds[4] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_height.png");
		
		ForwardRenderer.exitOnGLError("setupTexture");
	}
}
