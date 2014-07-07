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

public class Material implements Serializable {
	private static final long serialVersionUID = 1L;
	
	public static boolean MIPMAP_DEFAULT = true;
	public static int TEXTUREINDEX = 0;
	
	private static Logger LOGGER = getLogger();

	transient Vector3f ambient = new Vector3f(0.5f,0.5f,0.5f);
	transient Vector3f diffuse = new Vector3f(0.5f,0.5f,0.5f);
	transient Vector3f specular = new Vector3f(1f,1f,1f);
	transient float specularCoefficient = 0;
	transient float transparency = 1;
	transient float reflectiveness = 0.01f;
	transient float glossiness = 0.25f;

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

	transient String name = "";
	transient private String path;
	public MaterialInfo materialInfo = new MaterialInfo();

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

		program.setUniform("materialDiffuseColor", diffuse);
		program.setUniform("materialSpecularColor", specular);
		program.setUniform("materialSpecularCoefficient", specularCoefficient);
		program.setUniform("materialGlossiness", glossiness);
		
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

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
		this.materialInfo.name = name;
	}
	@Override
	public String toString() {
		return name + this.textures.size();
	}
	public boolean isTextureLess() {
		return textureLess;
	}
	public void setTextureLess(boolean textureLess) {
		this.textureLess = textureLess;
	}
	public void setAmbient(Vector3f ambient) {
		this.ambient = ambient;
		this.materialInfo.ambient = ambient;
	}
	public void setDiffuse(Vector3f diffuse) {
		this.diffuse = diffuse;
		this.materialInfo.diffuse = diffuse;
	}
	public void setSpecular(Vector3f specular) {
		this.specular = specular;
		this.materialInfo.specular = specular;
	}
	public void setSpecularCoefficient(float specularCoefficient) {
		this.specularCoefficient = specularCoefficient;
		this.materialInfo.specularCoefficient = specularCoefficient;
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
		this.materialInfo.reflectiveness = reflectiveness;
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

	public float getGlossiness() {
		return glossiness;
	}

	public void setGlossiness(float glossiness) {
		this.glossiness = glossiness;
		this.materialInfo.glossiness = glossiness;
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
