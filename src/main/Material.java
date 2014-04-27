package main;

import static main.log.ConsoleLogger.getLogger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import main.Material.MAP;
import main.util.Util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import org.newdawn.slick.opengl.Texture;

public class Material implements IEntity {
	
	public static final String TEXTUREASSETSPATH = "src/assets/textures/";
	
	public static boolean MIPMAP_DEFAULT = true;
	
	public static Map<String, Material> LIBRARY = new HashMap<>();
	public static Map<String, Texture> TEXTURES = new HashMap();
	
	private static Logger LOGGER = getLogger();
	
	public enum MAP {
		DIFFUSE("diffuseMap", 0),
		NORMAL("normalMap", 1),
		SPECULAR("specularMap", 2),
		OCCLUSION("occlusionMap", 3),
		HEIGHT("heightMap", 3);
		
		public final String shaderVariableName;
		public final int textureSlot;

		MAP(String shaderVariableName, int textureSlot) {
			this.shaderVariableName = shaderVariableName;
			this.textureSlot = textureSlot;
		}
	}

	public Map<MAP, String> textures = new HashMap<>();
	private boolean textureLess = false;

	private String name;
	
	public static int textureIndex = 0;

	public Material(Renderer renderer, String path, String diffuse, String... maps) {
		setup(path, diffuse, maps);
	}
	public Material(String path, String diffuse, String... maps) {
		setup(path, diffuse, maps);
	}

	public Material() {
	}
	
	public void setup(String path, String diffuse, String... maps) {
		if (path == null || path == "") {
			path = TEXTUREASSETSPATH;
		}
		MAP[] allMaps = MAP.values();
		
		String finalPath = path + diffuse;
		addTexture(allMaps[0], finalPath, Util.loadTexture(finalPath));
		
		for (int i = 0; i < maps.length; i++) {
			String map = maps[i];
			finalPath = path + map;
			addTexture(allMaps[i+1], finalPath, Util.loadTexture(finalPath));
		}
		
		LIBRARY.put(path, this);
	}
	
	private void addTexture(MAP map, String path, Texture texture) {
		if (TEXTURES.containsKey(path)) {
			LOGGER.log(Level.WARNING, String.format("Texture already loaded: %s", path));
		} else {
			TEXTURES.put(path, texture);
			LOGGER.log(Level.INFO, String.format("Texture loaded to atlas: %s", path));
		}
		textures.put(map, path);
	}
	public void loadAndAddTexture(MAP map, String path) {
		if (TEXTURES.containsKey(path)) {
			LOGGER.log(Level.WARNING, String.format("Texture already loaded: %s", path));
		} else {
			Texture texture = Util.loadTexture(path);
			TEXTURES.put(path, texture);
			LOGGER.log(Level.INFO, String.format("Texture loaded to atlas: %s", path));
		}
		textures.put(map, path);
		
	}
	
//	public void setup(ForwardRenderer renderer, String path) {
//		String texture = "wood";
//		texture = "stone";
//		texIds[0] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_diffuse.png");
//		texIds[1] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_normal.png");
//		texIds[2] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_specular.png");
//		texIds[3] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_occlusion.png");
//		texIds[4] = ForwardRenderer.loadTextureToGL("/assets/textures/" + texture + "_height.png");
//		
//		ForwardRenderer.exitOnGLError("setupTexture");
//	}

	public boolean hasSpecularMap() {
		return textures.containsKey(MAP.SPECULAR);
	}
	public boolean hasNormalMap() {
		return textures.containsKey(MAP.NORMAL);
	}
	public boolean hasDiffuseMap() {
		return !isTextureLess() && textures.containsKey(MAP.DIFFUSE);
	}
	
	public void setTexturesActive(Program program) {
		if (!program.needsTextures()) {
			return;
		}
				
		for (Map.Entry<MAP, String> entry : textures.entrySet()) {
			MAP map = entry.getKey();
			String path = entry.getValue();
			Texture texture = TEXTURES.get(path);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
//			texture.bind();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID());
			GL20.glUniform1f(GL20.glGetUniformLocation(program.getId(), map.shaderVariableName + "Width"), texture.getWidth());
			GL20.glUniform1f(GL20.glGetUniformLocation(program.getId(), map.shaderVariableName + "Height"), texture.getHeight());
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", map, texture.getTextureID(), materialProgram.getId(), map.textureSlot));
		}

		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "hasDiffuseMap"), hasDiffuseMap()? 1: 0);
		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "hasNormalMap"), hasNormalMap()? 1: 0);
		GL20.glUniform1i(GL20.glGetUniformLocation(program.getId(), "hasSpecularMap"), hasSpecularMap()? 1: 0);
		
	}
	public void setTexturesInactive() {
				
		for (Map.Entry<MAP, String> entry : textures.entrySet()) {
			MAP map = entry.getKey();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
//			texture.bind();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", map, texture.getTextureID(), materialProgram.getId(), map.textureSlot));
		}
		
	}

	@Override
	public void destroy() {
//		GL11.glDeleteTextures(texIds[0]);
//		GL11.glDeleteTextures(texIds[1]);
//		GL11.glDeleteTextures(texIds[2]);
//		GL11.glDeleteTextures(texIds[3]);
//		GL11.glDeleteTextures(texIds[4]);
	}

	@Override
	public Vector3f getPosition() {
		return null;
	}

	@Override
	public void move(Vector3f amount) {
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	@Override
	public Material getMaterial() {
		return this;
	}
	public boolean isTextureLess() {
		return textureLess;
	}
	public void setTextureLess(boolean textureLess) {
		this.textureLess = textureLess;
	}
}
