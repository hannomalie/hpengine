package main.renderer.material;

import static main.log.ConsoleLogger.getLogger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import main.World;
import main.model.IEntity;
import main.renderer.material.MaterialFactory.MaterialInfo;
import main.shader.Program;
import main.texture.Texture;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Vector3f;

public class Material implements IEntity, Serializable {
	
	public static boolean MIPMAP_DEFAULT = true;
	public static int TEXTUREINDEX = 0;
	
	private static Logger LOGGER = getLogger();

	transient Vector3f ambient = new Vector3f(0.5f,0.5f,0.5f);
	transient Vector3f diffuse = new Vector3f(0.5f,0.5f,0.5f);
	transient Vector3f specular = new Vector3f(1f,1f,1f);
	transient float specularCoefficient = 0;
	transient float transparency = 1;
	transient float reflectiveness = 0.2f;

	transient private Program firstPassProgram;
	
	public enum MAP {
		DIFFUSE("diffuseMap", 0),
		NORMAL("normalMap", 1),
		SPECULAR("specularMap", 2),
		OCCLUSION("occlusionMap", 3),
		HEIGHT("heightMap", 4),
		REFLECTION("reflectionMap", 5);
		
		public final String shaderVariableName;
		public final int textureSlot;

		MAP(String shaderVariableName, int textureSlot) {
			this.shaderVariableName = shaderVariableName;
			this.textureSlot = textureSlot;
		}
	}

	transient public MaterialMap textures = new MaterialMap();
	
	transient private boolean textureLess = false;
	transient boolean setUp = false;

	transient private String name = "";
	transient private String path;
	public MaterialInfo materialInfo;

	protected Material() { }

	void addTexture(MAP map, Texture texture) {
		textures.textures.put(map, texture);
	}
	
	public boolean hasSpecularMap() {
		return textures.textures.containsKey(MAP.SPECULAR);
	}
	public boolean hasNormalMap() {
		return textures.textures.containsKey(MAP.NORMAL);
	}
	public boolean hasDiffuseMap() {
		return !isTextureLess() && textures.textures.containsKey(MAP.DIFFUSE);
	}
	
	public void setTexturesActive(Program program) {
		if (!program.needsTextures()) {
			return;
		}
				
		for (Entry<MAP, Texture> entry : textures.textures.entrySet()) {
			MAP map = entry.getKey();
			Texture texture = entry.getValue();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
			texture.bind();
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
		for (Map.Entry<MAP, Texture> entry : textures.textures.entrySet()) {
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
		return name + this.textures.size();
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

	public void setProgram(Program firstPassProgram) {
		this.firstPassProgram = firstPassProgram;
	}
	public float getReflectiveness() {
		return reflectiveness;
	}

	public void setReflectiveness(float reflectiveness) {
		this.reflectiveness = reflectiveness;
	}
	public Vector3f getAmbient() {
		return ambient;
	}

	public Vector3f getDiffuse() {
		return diffuse;
	}

	public Vector3f getSpecular() {
		return specular;
	}

	public float getSpecularCoefficient() {
		return specularCoefficient;
	}

	public static boolean write(Material material, String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(getDirectory() + fileName + ".hpmaterial");
			out = new ObjectOutputStream(fos);
			out.writeObject(material);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static String getDirectory() {
		return World.WORKDIR_NAME + "/assets/materials/";
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Material)) {
			return false;
		}
		
		Material m = (Material) other;
		return (m.ambient.equals(ambient) &&
				m.diffuse.equals(diffuse) &&
				m.specular.equals(specular) &&
				m.specularCoefficient == specularCoefficient &&
//				m.textures.equals(textures) &&
//				m.name.equals(name) &&
				m.reflectiveness == reflectiveness &&
				m.firstPassProgram.equals(firstPassProgram));
	}

}
