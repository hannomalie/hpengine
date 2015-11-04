package renderer.material;

import engine.AppContext;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.constants.GlTextureTarget;
import renderer.material.MaterialFactory.MaterialInfo;
import shader.Bufferable;
import shader.Program;
import shader.ProgramFactory;
import shader.ShaderDefine;
import texture.Texture;
import util.stopwatch.GPUProfiler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import static log.ConsoleLogger.getLogger;

public class Material implements Serializable, Bufferable {
	public enum MaterialType {
		DEFAULT,
		FOLIAGE,
		UNLIT
	}

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

		Program firstPassProgram = null;

		firstPassProgram = getFirstpassProgramForShaderDefinitions(renderer, firstPassProgram);
		setProgram(firstPassProgram);
	}

	protected Material() { }

	private Program getFirstpassProgramForShaderDefinitions(Renderer renderer, Program firstPassProgram) {
		String definesString = ShaderDefine.getDefinesString(materialInfo.maps.getTextures().keySet());
		
		if(materialInfo.hasCustomVertexShader() && !materialInfo.hasCustomFragmentShader()) {
			firstPassProgram = renderer.getProgramFactory().getProgram(materialInfo.vertexShader, ProgramFactory.FIRSTPASS_DEFAULT_FRAGMENTSHADER_FILE);
		} else if(!materialInfo.hasCustomVertexShader() && materialInfo.hasCustomFragmentShader()) {
			firstPassProgram = renderer.getProgramFactory().getProgram(ProgramFactory.FIRSTPASS_DEFAULT_VERTEXSHADER_FILE, materialInfo.fragmentShader);
		} else if(materialInfo.hasCustomVertexShader() && materialInfo.hasCustomFragmentShader()) {
			firstPassProgram = renderer.getProgramFactory().getProgram(materialInfo.vertexShader, materialInfo.fragmentShader);
		}
		firstPassProgram = logAndFallBackIfNull(renderer, firstPassProgram, definesString);
		return firstPassProgram;
	}

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
	public boolean hasDiffuseMap() {
		return !isTextureLess() && materialInfo.maps.getTextures().containsKey(MAP.DIFFUSE);
	}
	
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
	public Program getFirstPassProgram() {
		return materialInfo.firstPassProgram;
	}

	public void setProgram(Program firstPassProgram) {
		materialInfo.firstPassProgram = firstPassProgram;
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

	public boolean hasCustomVertexShader() {
		return materialInfo.hasCustomVertexShader();
	}

	public boolean hasCustomFragmentShader() {
		return materialInfo.hasCustomFragmentShader();
	}

	public String getGeometryShader() {
		return materialInfo.getGeometryShader();
	}

	public void setGeometryShader(String geometryShader) {
		materialInfo.setGeometryShader(geometryShader);
	}

	public String getVertexShader() {
		return materialInfo.getVertexShader();
	}

	public void setVertexShader(String vertexShader) {
		materialInfo.setVertexShader(vertexShader);
	}

	public String getFragmentShader() {
		return materialInfo.getFragmentShader();
	}

	public void setFragmentShader(String fragmentShader) {
		materialInfo.setFragmentShader(fragmentShader);
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
		return (mi.diffuse.equals(materialInfo.diffuse) &&
//				m.textures.equals(textures) &&
//				m.name.equals(name) &&
				mi.roughness == materialInfo.roughness &&
				mi.firstPassProgram.equals(materialInfo.firstPassProgram));
	}

	public void setEnvironmentMapType(ENVIRONMENTMAPTYPE type) {
		this.materialInfo.environmentMapType = type;
	}
	
	public ENVIRONMENTMAPTYPE getEnvironmentMapType() {
		return this.materialInfo.environmentMapType;
	}


//	@Override
//	public int getSizePerObject() {
//		return -1;
//	}

	@Override
	public float[] get() {
		float[] floats = new float[13];
		int index = 0;
		floats[index++] = materialInfo.diffuse.x;
		floats[index++] = materialInfo.diffuse.y;
		floats[index++] = materialInfo.diffuse.z;
		floats[index++] = materialInfo.metallic;
		floats[index++] = materialInfo.roughness;
		floats[index++] = materialInfo.ambient;
		floats[index++] = materialInfo.parallaxBias;
		floats[index++] = materialInfo.parallaxScale;
		floats[index++] = materialInfo.transparency;
		floats[index++] = materialInfo.materialType.ordinal();
		floats[index++] = hasDiffuseMap() ? 1 : 0;
		floats[index++] = hasNormalMap() ? 1 : 0;
		floats[index++] = hasSpecularMap() ? 1 : 0;
		return floats;
	}

}
