package main.renderer.material;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.util.vector.Vector3f;

import main.renderer.Renderer;
import main.renderer.material.Material.MAP;
import main.renderer.material.MaterialFactory.MaterialInfo;
import main.shader.Program;
import main.shader.ShaderDefine;
import main.texture.Texture;
import main.util.Util;

public class MaterialFactory {

	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
	
	private Renderer renderer;
	public Map<String, Material> MATERIALS = new HashMap<>();

	private Material defaultMaterial;

	public MaterialFactory(Renderer renderer) {
		this.renderer = renderer;
		defaultMaterial = getMaterial(new HashMap<MAP,String>(){{
			put(MAP.DIFFUSE,"assets/textures/default.dds");
		}});
	}

	public Material getMaterial(MaterialInfo materialInfo) {
		Material material = new Material();
		if(materialInfo.name == null || materialInfo.name == "") {
			materialInfo.name = "Material " + count++;
		}
		material.setName(materialInfo.name);
		material.textures = materialInfo.maps;
		material.ambient = materialInfo.ambient;
		material.diffuse = materialInfo.diffuse;
		material.specular = materialInfo.specular;
		material.specularCoefficient = materialInfo.specularCoefficient;
		//material.transparency = materialInfo.transparency;
		initMaterial(material);
		return material;
	}

	public Material getMaterial(HashMap<MAP, String> hashMap) {
		HashMap<MAP, Texture> textures = new HashMap<>();
		
		for (MAP map : hashMap.keySet()) {
			try {
				textures.put(map, renderer.getTextureFactory().getTexture(hashMap.get(map)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Material material = getMaterial(new MaterialInfo(textures));
		return material;
	}

	private void initMaterial(Material material) {
		Program firstPassProgram = Program.firstPassProgramForDefines(ShaderDefine.getDefinesString(material.textures.keySet()));
		material.setProgram(firstPassProgram);
		MATERIALS.put(material.getName(), material);
		material.setUp = true;
	}

	public Material get(String materialName) {
		return MATERIALS.get(materialName);
	}
	
	public static final class MaterialInfo {
		public MaterialInfo(HashMap<MAP, Texture> maps) {
			this.maps = maps;
		}

		public MaterialInfo setName(String name) {
			this.name = name;
			return this;
		}

		public MaterialInfo() {
		}

		public HashMap<MAP,Texture> maps = new HashMap<>();
		public String name = "";
		public Vector3f ambient = new Vector3f();
		public Vector3f diffuse = new Vector3f();
		public Vector3f specular = new Vector3f(0,0,0);
		public float specularCoefficient = 1f;
	}

	public void putAll(Map<String, MaterialInfo> materialLib) {
		for (String key : materialLib.keySet()) {
			MATERIALS.put(key, getMaterial(materialLib.get(key)));
		}
	}

	public Material getDefaultMaterial() {
		return defaultMaterial;
	}
}
