package main;

import static main.log.ConsoleLogger.getLogger;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class Material implements IEntity {
	
	private static Logger LOGGER = getLogger();
	
	public static String[] mapNames = {"diffuseMap", "normalMap", "specularMap", "occlusionMap", "heightMap"};
	
	public static int textureIndex = 0;

	protected int[] texIds = new int[] {0, 1, 2, 3, 4};

	public Material(ForwardRenderer renderer) {
		setup(renderer);
	}
	
	public void setup(ForwardRenderer renderer) {
		String texture = "wood";
		texture = "stone";
		texIds[0] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_diffuse.png");
		texIds[1] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_normal.png");
		texIds[2] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_specular.png");
		texIds[3] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_occlusion.png");
		texIds[4] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_height.png");
		
		ForwardRenderer.exitOnGLError("setupTexture");
	}

	@Override
	public void update() {
		
	}

	@Override
	public void draw() {

	}
	
	public void setTexturesActive() {
		Program materialProgram = ForwardRenderer.getMaterialProgram();
		materialProgram.use();

		for (int i = 0; i < mapNames.length; i++) {
			String name = mapNames[i];
			int texture = texIds[i];
			int binding = 1;
			
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", name, texture, materialProgram.getId(), i));

			GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
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

	@Override
	public void drawShadow() {

	}
}
