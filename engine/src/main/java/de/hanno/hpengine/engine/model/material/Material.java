package de.hanno.hpengine.engine.model.material;

import de.hanno.hpengine.engine.DirectoryManager;
import de.hanno.hpengine.log.ConsoleLogger;
import de.hanno.hpengine.engine.model.texture.Texture;
import org.apache.commons.io.FilenameUtils;
import org.joml.Vector3f;
import de.hanno.hpengine.engine.graphics.buffer.Bufferable;
import de.hanno.hpengine.engine.graphics.shader.Program;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.logging.Logger;

public class Material implements Serializable, Bufferable {

	private int materialIndex;

	public enum MaterialType {
		DEFAULT,
		FOLIAGE,
		UNLIT
	}

	private static final long serialVersionUID = 1L;
	
	public static boolean MIPMAP_DEFAULT = true;

	private static Logger LOGGER = ConsoleLogger.getLogger();

	public enum MAP {
		DIFFUSE("diffuseMap", 0),
		NORMAL("normalMap", 1),
		SPECULAR("specularMap", 2),
		OCCLUSION("occlusionMap", 3),
		HEIGHT("heightMap", 4),
		REFLECTION("reflectionMap", 5),
		ENVIRONMENT("environmentMap", 6),
		ROUGHNESS("roughnessMap", 7);
		
		public final String shaderVariableName;
		public final int textureSlot;

		MAP(String shaderVariableName, int textureSlot) {
			this.shaderVariableName = shaderVariableName;
			this.textureSlot = textureSlot;
		}
	}
	
	public enum ENVIRONMENTMAPTYPE {
		PROVIDED,
		GENERATED
	}

	transient boolean initialized = false;

	private MaterialInfo materialInfo = new MaterialInfo();

	public void init(MaterialFactory materialFactory) {
		materialIndex = materialFactory.indexOf(this);
		for(MAP map : materialInfo.maps.getTextureNames().keySet()) {
			String name = materialInfo.maps.getTextureNames().get(map);
			try {
				Texture tex;
				if(map.equals(MAP.ENVIRONMENT)) {
                    tex = materialFactory.getTextureFactory().getCubeMap(materialFactory.getTextureFactory(), name);
					if(tex == null) {
                        tex = materialFactory.getTextureFactory().getCubeMap();
					}
				} else {
                    tex = materialFactory.getTextureFactory().getTexture(name);
				}
				materialInfo.maps.getTextures().put(map, tex);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (!materialInfo.maps.getTextures().containsKey(MAP.ENVIRONMENT)) {
            materialInfo.maps.getTextures().put(MAP.ENVIRONMENT, materialFactory.getTextureFactory().getCubeMap());
		}
		initialized = true;
	}

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
	public boolean hasDiffuseMap() { return !isTextureLess() && materialInfo.maps.getTextures().containsKey(MAP.DIFFUSE); }
    public boolean hasHeightMap() { return materialInfo.maps.getTextures().containsKey(MAP.HEIGHT); }
    public boolean hasOcclusionMap() { return materialInfo.maps.getTextures().containsKey(MAP.OCCLUSION); }
    public boolean hasRoughnessMap() { return materialInfo.maps.getTextures().containsKey(MAP.ROUGHNESS); }

	public void setTexturesActive(Program program) {
		program.setUniform("materialIndex", materialIndex);

//		for (Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
//			MAP map = entry.getKey();
//			Texture de.hanno.hpengine.texture = entry.getValue();
//			de.hanno.hpengine.texture.bind(map.textureSlot);
//		}

//        OpenGLContext.getInstance().bindTextures(0, materialInfo.maps.getTextures().entrySet().size(), materialInfo.getTextureIds());
	}
	public void setTexturesActive(Program program, boolean withoutSetUsed) {
//		for (Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
//			MAP map = entry.getKey();
//            if(map.equals(MAP.OCCLUSION)) { continue; }
//			Texture de.hanno.hpengine.texture = entry.getValue();
//			de.hanno.hpengine.texture.bind(map.textureSlot, withoutSetUsed);
//		}

//        GPUProfiler.start("bindTextures");
//        OpenGLContext.getInstance().bindTextures(0, materialInfo.maps.getTextures().entrySet().size(), materialInfo.getTextureIds());
        if(!withoutSetUsed) {
            for (Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
                MAP map = entry.getKey();
                if(map.equals(MAP.OCCLUSION)) { continue; }
                Texture texture = entry.getValue();
                texture.setUsedNow();
            }
        }
//        GPUProfiler.end();
	}

	public void setTexturesUsed() {
		materialInfo.maps.getTextures().forEach((key, value) -> value.setUsedNow());
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
	public void setDiffuse(Vector3f diffuse) {
		materialInfo.diffuse = diffuse;
	}

	public Vector3f getDiffuse() {
		return materialInfo.diffuse;
	}

	public float getRoughness() {
		return materialInfo.roughness;
	}

	public void setRoughness(float roughness) {
		materialInfo.roughness = roughness;
	}
	public float getMetallic() {
		return materialInfo.metallic;
	}

	public void setMetallic(float metallic) {
		materialInfo.metallic = metallic;
	}
	public float getAmbient() {
		return materialInfo.ambient;
	}
	public void setAmbient(float ambient) {
		materialInfo.ambient = ambient;
	}

	public float getTransparency() {
		return materialInfo.transparency;
	}
	public void setTransparency(float transparency) {
		materialInfo.transparency = transparency;
	}
	public float getParallaxScale() {
		return materialInfo.parallaxScale;
	}
	public void setParallaxScale(float parallaxScale) {
		materialInfo.parallaxScale = parallaxScale;
	}
	public float getParallaxBias() {
		return materialInfo.parallaxBias;
	}
	public void setParallaxBias(float parallaxBias) {
		materialInfo.parallaxBias = parallaxBias;
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
				fos.close();
				return true;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}

	public static String getDirectory() {
		return DirectoryManager.WORKDIR_NAME + "/assets/materials/";
	}

	public MaterialInfo getMaterialInfo() {
		return materialInfo;
	}

	public void setMaterialInfo(MaterialInfo materialInfo) {
		this.materialInfo = materialInfo;
	}

	public MaterialType getMaterialType() {
		return materialInfo.materialType;
	}

	public void setMaterialType(MaterialType materialType) {
		this.materialInfo.materialType = materialType;
	}

    public Collection<Texture> getTextures() {
        return materialInfo.maps.getTextures().values();
    }

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Material)) {
			return false;
		}
		
		Material m = (Material) other;
		return materialInfo.name.equals(m.getName());
	}

	public void setEnvironmentMapType(ENVIRONMENTMAPTYPE type) {
		this.materialInfo.environmentMapType = type;
	}
	
	public ENVIRONMENTMAPTYPE getEnvironmentMapType() {
		return this.materialInfo.environmentMapType;
	}


	@Override
	public void putToBuffer(ByteBuffer buffer) {
		buffer.putFloat(materialInfo.diffuse.x);
		buffer.putFloat(materialInfo.diffuse.y);
		buffer.putFloat(materialInfo.diffuse.z);
		buffer.putFloat(materialInfo.metallic);
		buffer.putFloat(materialInfo.roughness);
		buffer.putFloat(materialInfo.ambient);
		buffer.putFloat(materialInfo.parallaxBias);
		buffer.putFloat(materialInfo.parallaxScale);
		buffer.putFloat(materialInfo.transparency);
		buffer.putFloat(materialInfo.materialType.ordinal());
		buffer.putInt(hasDiffuseMap() ? 1 : 0);
		buffer.putInt(hasNormalMap() ? 1 : 0);
		buffer.putInt(hasSpecularMap() ? 1 : 0);
		buffer.putInt(hasHeightMap() ? 1 : 0);
		buffer.putInt(hasOcclusionMap() ? 1 : 0);
		buffer.putInt(hasRoughnessMap() ? 1 : 0);
		buffer.putDouble(hasDiffuseMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.DIFFUSE).getHandle()) : 0);
		buffer.putDouble(hasNormalMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.NORMAL).getHandle()) : 0);
		buffer.putDouble(hasSpecularMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.SPECULAR).getHandle()) : 0);
		buffer.putDouble(hasHeightMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.HEIGHT).getHandle()) : 0);
		buffer.putDouble(hasOcclusionMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.OCCLUSION).getHandle()) : 0);
		buffer.putDouble(hasRoughnessMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.ROUGHNESS).getHandle()) : 0);
		buffer.putInt(0);
		buffer.putInt(0);
	}

	@Override
	public int getBytesPerObject() {
		return 24 * Double.BYTES;
	}
}
