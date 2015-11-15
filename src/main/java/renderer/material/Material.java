package renderer.material;

import engine.AppContext;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.constants.GlTextureTarget;
import renderer.material.MaterialFactory.MaterialInfo;
import shader.*;
import texture.Texture;

import java.io.*;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;
import static shader.Shader.*;

public class Material implements Serializable, Bufferable {
	public enum MaterialType {
		DEFAULT,
		FOLIAGE,
		UNLIT
	}

	private static final long serialVersionUID = 1L;
	
	public static boolean MIPMAP_DEFAULT = true;

	private static Logger LOGGER = getLogger();

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

	transient private Renderer renderer;

	public void init(Renderer renderer) {
		this.renderer = renderer;
		for(MAP map : materialInfo.maps.getTextureNames().keySet()) {
			String name = materialInfo.maps.getTextureNames().get(map);
			try {
				Texture tex;
				if(map.equals(MAP.ENVIRONMENT)) {
					tex = renderer.getTextureFactory().getCubeMap(name);
					if(tex == null) {
						tex = renderer.getEnvironmentMap();
					}
				} else {
					tex = renderer.getTextureFactory().getTexture(name);
				}
				materialInfo.maps.getTextures().put(map, tex);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (!materialInfo.maps.getTextures().containsKey(MAP.ENVIRONMENT)) {
			materialInfo.maps.getTextures().put(MAP.ENVIRONMENT, renderer.getEnvironmentMap());
		}
	}

	protected Material() { }

	private Program logAndFallBackIfNull(Renderer renderer, Program firstPassProgram, String definesString) {
		if(firstPassProgram == null) {
//			System.err.println("File not found for material " + materialInfo.name);
			firstPassProgram = renderer.getProgramFactory().getProgram(definesString);
		}
		return firstPassProgram;
	}

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
		program.setUniform("materialIndex", AppContext.getInstance().getRenderer()
				.getMaterialFactory().indexOf(this));

		if (!program.needsTextures()) {
			return;
		}
				
		for (Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
			MAP map = entry.getKey();
			Texture texture = entry.getValue();
			texture.bind(map.textureSlot);
		}
	}

	public void setTexturesInactive() {
		for (Map.Entry<MAP, Texture> entry : materialInfo.maps.getTextures().entrySet()) {
			MAP map = entry.getKey();
			OpenGLContext.getInstance().bindTexture(map.textureSlot, GlTextureTarget.TEXTURE_2D, 0);
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
		return AppContext.WORKDIR_NAME + "/assets/materials/";
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

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Material)) {
			return false;
		}
		
		Material m = (Material) other;
		MaterialInfo mi = m.getMaterialInfo();
		return materialInfo.equals(((Material) other).getMaterialInfo());
	}

	public void setEnvironmentMapType(ENVIRONMENTMAPTYPE type) {
		this.materialInfo.environmentMapType = type;
	}
	
	public ENVIRONMENTMAPTYPE getEnvironmentMapType() {
		return this.materialInfo.environmentMapType;
	}


	@Override
	public int getSizePerObject() {
		return 22;
	}

	@Override
	public double[] get() {
		double[] doubles = new double[getSizePerObject()];
		int index = 0;
		doubles[index++] = materialInfo.diffuse.x;
		doubles[index++] = materialInfo.diffuse.y;
		doubles[index++] = materialInfo.diffuse.z;
		doubles[index++] = materialInfo.metallic;
		doubles[index++] = materialInfo.roughness;
		doubles[index++] = materialInfo.ambient;
		doubles[index++] = materialInfo.parallaxBias;
		doubles[index++] = materialInfo.parallaxScale;
		doubles[index++] = materialInfo.transparency;
		doubles[index++] = materialInfo.materialType.ordinal();
		doubles[index++] = hasDiffuseMap() ? 1 : 0;
		doubles[index++] = hasNormalMap() ? 1 : 0;
        doubles[index++] = hasSpecularMap() ? 1 : 0;
        doubles[index++] = hasHeightMap() ? 1 : 0;
        doubles[index++] = hasOcclusionMap() ? 1 : 0;
        doubles[index++] = hasRoughnessMap() ? 1 : 0;
        doubles[index++] = hasDiffuseMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.DIFFUSE).getHandle()) : 0;
        doubles[index++] = hasNormalMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.NORMAL).getHandle()) : 0;
        doubles[index++] = hasSpecularMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.SPECULAR).getHandle()) : 0;
        doubles[index++] = hasHeightMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.HEIGHT).getHandle()) : 0;
        doubles[index++] = hasOcclusionMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.OCCLUSION).getHandle()) : 0;
        doubles[index++] = hasRoughnessMap() ? Double.longBitsToDouble(materialInfo.maps.getTextures().get(MAP.ROUGHNESS).getHandle()) : 0;
		return doubles;
	}

}
