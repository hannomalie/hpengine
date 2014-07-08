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
import main.renderer.Renderer;
import main.renderer.material.MaterialFactory.MaterialInfo;
import main.shader.Program;
import main.shader.ShaderDefine;
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
	
	public enum MAP {
		DIFFUSE("diffuseMap", 0),
		NORMAL("normalMap", 1),
		SPECULAR("specularMap", 2),
		OCCLUSION("occlusionMap", 3),
		HEIGHT("heightMap", 4),
		REFLECTION("reflectionMap", 5),
		ENVIRONMENT("environmentMap", 6);
		
		public final String shaderVariableName;
		public final int textureSlot;

		MAP(String shaderVariableName, int textureSlot) {
			this.shaderVariableName = shaderVariableName;
			this.textureSlot = textureSlot;
		}
	}

	transient boolean initialized = false;

	private MaterialInfo materialInfo = new MaterialInfo();

	protected Material() { }

	void addTexture(MAP map, Texture texture) {
		materialInfo.put(map, texture);
	}
	
	public boolean hasSpecularMap() {
		return materialInfo.maps.getTextures().containsKey(MAP.SPECULAR);
	}
	public boolean hasNormalMap() {
		return materialInfo.maps.getTextures().containsKey(MAP.NORMAL);
	}
	public boolean hasDiffuseMap() {
		return !isTextureLess() && materialInfo.maps.getTextures().containsKey(MAP.DIFFUSE);
	}
	
	public void setTexturesActive(Program program) {
		if (!program.needsTextures()) {
			return;
		}
				
		for (Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
			MAP map = entry.getKey();
			Texture texture = entry.getValue();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
			texture.bind();
			program.setUniform(map.shaderVariableName + "Width", texture.getWidth());
			program.setUniform(map.shaderVariableName + "Height", texture.getHeight());
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", map, texture.getTextureID(), materialProgram.getId(), map.textureSlot));
		}

		program.setUniform("materialDiffuseColor", getDiffuse());
		program.setUniform("materialSpecularColor", getSpecular());
		program.setUniform("materialSpecularCoefficient", getSpecularCoefficient());
		program.setUniform("materialGlossiness", getGlossiness());
		
	}

	public void setTexturesInactive() {
		for (Map.Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
			MAP map = entry.getKey();
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + map.textureSlot);
//			texture.bind();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
//			LOGGER.log(Level.INFO, String.format("Setting %s (index %d) for Program %d to %d", map, texture.getTextureID(), materialProgram.getId(), map.textureSlot));
		}
		
	}

	public String getName() {
		return materialInfo.name;
	}
	public void setName(String name) {
		this.materialInfo.name = name;
	}
	@Override
	public String toString() {
		return materialInfo.name;
	}
	public boolean isTextureLess() {
		return materialInfo.textureLess;
	}
	public void setTextureLess(boolean textureLess) {
		materialInfo.textureLess = textureLess;
	}
	public void setAmbient(Vector3f ambient) {
		materialInfo.ambient = ambient;
	}
	public void setDiffuse(Vector3f diffuse) {
		materialInfo.diffuse = diffuse;
	}
	public void setSpecular(Vector3f specular) {
		materialInfo.specular = specular;
	}
	public void setSpecularCoefficient(float specularCoefficient) {
		materialInfo.specularCoefficient = specularCoefficient;
	}
	public Program getFirstPassProgram() {
		return materialInfo.firstPassProgram;
	}

	public void setProgram(Program firstPassProgram) {
		materialInfo.firstPassProgram = firstPassProgram;
	}
	public float getReflectiveness() {
		return materialInfo.reflectiveness;
	}

	public void setReflectiveness(float reflectiveness) {
		materialInfo.reflectiveness = reflectiveness;
	}
	public Vector3f getAmbient() {
		return materialInfo.ambient;
	}

	public Vector3f getDiffuse() {
		return materialInfo.diffuse;
	}

	public Vector3f getSpecular() {
		return materialInfo.specular;
	}

	public float getSpecularCoefficient() {
		return materialInfo.specularCoefficient;
	}

	public float getGlossiness() {
		return materialInfo.glossiness;
	}

	public void setGlossiness(float glossiness) {
		materialInfo.glossiness = glossiness;
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
		MaterialInfo mi = m.getMaterialInfo();
		return (mi.ambient.equals(materialInfo.ambient) &&
				mi.diffuse.equals(materialInfo.diffuse) &&
				mi.specular.equals(materialInfo.specular) &&
				mi.specularCoefficient == materialInfo.specularCoefficient &&
//				m.textures.equals(textures) &&
//				m.name.equals(name) &&
				mi.reflectiveness == materialInfo.reflectiveness &&
				mi.firstPassProgram.equals(materialInfo.firstPassProgram));
	}

	public void init(Renderer renderer) {
		for(MAP map : materialInfo.maps.getTextureNames().keySet()) {
			String name = materialInfo.maps.getTextureNames().get(map);
			try {
				Texture tex = renderer.getTextureFactory().getTexture(name);
				materialInfo.maps.getTextures().put(map, tex);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (!materialInfo.maps.getTextures().containsKey(MAP.ENVIRONMENT)) {
			materialInfo.maps.getTextures().put(MAP.ENVIRONMENT, renderer.getEnvironmentMap());
		}
		
		Program firstPassProgram = null;
		
		if(materialInfo.hasCustomVertexShader() && materialInfo.hasCustomFragmentShader()) {
			firstPassProgram = renderer.getProgramFactory().getProgram(materialInfo.vertexShader, materialInfo.fragmentShader);
		} else {
			firstPassProgram = renderer.getProgramFactory().getProgram(ShaderDefine.getDefinesString(materialInfo.maps.getTextures().keySet()));
		}
		setProgram(firstPassProgram);
		
	}

	public MaterialInfo getMaterialInfo() {
		return materialInfo;
	}

	public void setMaterialInfo(MaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}

}
