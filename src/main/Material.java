package main;

import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class Material implements IEntity {
	
	private static String[] mapNames = {"diffuseMap", "normalMap", "specularMap", "occlusionMap", "heightMap"};
	
	public static int textureIndex = 0;

	protected int[] texIds = new int[] {0, 1, 2, 3, 4};

	public Material() {
		setup();
	}
	
	public void setup() {
		String texture = "wood";
		texture = "stone";
		texIds[0] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_diffuse.png", ForwardRenderer.getMaterialProgramId(), "diffuseMap", 0);
		texIds[1] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_normal.png", ForwardRenderer.getMaterialProgramId(), "normalMap", 1);
		texIds[2] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_specular.png", ForwardRenderer.getMaterialProgramId(), "specularMap", 2);
		texIds[3] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_occlusion.png", ForwardRenderer.getMaterialProgramId(), "occlusionMap", 3);
		texIds[4] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_height.png", ForwardRenderer.getMaterialProgramId(), "heightMap", 4);
		
		ForwardRenderer.exitOnGLError("setupTexture");
	}

	@Override
	public void update() {
		
	}

	@Override
	public void draw() {

	}
	
	public void setTexturesActive() {

		for (int i = 0; i < mapNames.length; i++) {
			String name = mapNames[i];
			int index = texIds[i];
			
			int bindingForTextureInShader = GL20.glGetUniformLocation(ForwardRenderer.getMaterialProgramId(), name);
			//System.out.println("Setting " + bindingForTextureInShader + " for " + name + " to " + index);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, index);
		}
	}

	@Override
	public void destroy() {
		GL11.glDeleteTextures(texIds[0]);
		GL11.glDeleteTextures(texIds[1]);
		GL11.glDeleteTextures(texIds[2]);
		GL11.glDeleteTextures(texIds[3]);
		GL11.glDeleteTextures(texIds[4]);
	}
}
