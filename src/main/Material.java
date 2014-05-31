package main;

import static main.log.ConsoleLogger.getLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import main.shader.Program;
import main.shader.ShaderDefine;
import main.util.Texture;
import main.util.Util;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Vector3f;

public class Material implements IEntity {
	
	public static final String TEXTUREASSETSPATH = "assets/textures/";
	
	public static boolean MIPMAP_DEFAULT = true;
	public static int TEXTUREINDEX = 0;
	public static Map<String, Material> MATERIALS = new HashMap<>();
	public static Map<String, Texture> TEXTURES = new HashMap<>();
	
	private static Logger LOGGER = getLogger();

	Vector3f ambient = new Vector3f(0.5f,0.5f,0.5f);
	Vector3f diffuse = new Vector3f(0.5f,0.5f,0.5f);
	Vector3f specular = new Vector3f(0.5f,0.5f,0.5f);
	float specularCoefficient = 1;
	float transparency = 1;
	private Program firstPassProgram;
	
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
	private boolean setUp = false;

	private String name = "";
	private String path;

	public Material(Renderer renderer, String path, String... maps) {
		setup(path, maps);
	}
//	public Material(String path, String... maps) {
//		setup(path, maps);
//	}

	public Material() {
	}
	
	public void setup(String path, String... maps) {
		if (path == null || path == "") {
			this.path = TEXTUREASSETSPATH;
		}
		MAP[] allMaps = MAP.values();
		
		for (int i = 0; i < maps.length; i++) {
			String map = maps[i];
			String finalPath = this.path + map;
			addTexture(allMaps[i], finalPath, Util.loadTexture(finalPath));
		}
		
		setup();
	}
	
	public void setup() {
		firstPassProgram = Program.firstPassProgramForDefines(ShaderDefine.getDefinesString(textures.keySet()));
		MATERIALS.put(name, this);
		setUp = true;
	}
	
	private void addTexture(MAP map, String path, main.util.Texture texture) {
		if (TEXTURES.containsKey(path)) {
//			LOGGER.log(Level.WARNING, String.format("Texture already loaded: %s", path));
		} else {
			TEXTURES.put(path, texture);
//			LOGGER.log(Level.INFO, String.format("Texture loaded to atlas: %s", path));
		}
		textures.put(map, path);
	}
	public void loadAndAddTexture(MAP map, String path) {
		if (TEXTURES.containsKey(path)) {
//			LOGGER.log(Level.WARNING, String.format("Texture already loaded: %s", path));
		} else {
			main.util.Texture texture = Util.loadTexture(path);
			TEXTURES.put(path, texture);
//			LOGGER.log(Level.INFO, String.format("Texture loaded to atlas: %s", path));
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
			main.util.Texture texture = TEXTURES.get(path);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
			texture.bind();
//			GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID());
			// TODO: CHECK IF PROPER GETTER IS USED
			program.setUniform(map.shaderVariableName + "Width", texture.getWidth());
			program.setUniform(map.shaderVariableName + "Height", texture.getHeight());
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", map, texture.getTextureID(), materialProgram.getId(), map.textureSlot));
		}

//		program.setUniform("hasDiffuseMap", hasDiffuseMap()? 1: 0);
//		program.setUniform("hasNormalMap", hasNormalMap()? 1: 0);
//		program.setUniform("hasSpecularMap", hasSpecularMap()? 1: 0);
		program.setUniform("materialDiffuseColor", diffuse);
		program.setUniform("materialSpecularColor", specular);
		program.setUniform("materialSpecularCoefficient", specularCoefficient);
		//program.setUniform("materialAmbientColor", ambient);
		//program.setUniform("materialTransparency", transparency);
		
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
	public void setAmbient(Vector3f ambient) {
		this.ambient = ambient;
	}
	public void setDiffuse(Vector3f diffuse) {
		this.diffuse = diffuse;
	}
	public void setSpecular(Vector3f specular) {
		this.specular = specular;
	}
	public void setSpecularCoefficient(float specularCoefficient) {
		this.specularCoefficient = specularCoefficient;
	}
	
	@Override
	public boolean isSelected() {
		return false;
	}

	@Override
	public void setSelected(boolean selected) {
	}
	public Program getFirstPassProgram() {
		return firstPassProgram;
	}
}
