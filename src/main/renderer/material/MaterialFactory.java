package main.renderer.material;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import main.renderer.Renderer;
import main.renderer.material.Material.ENVIRONMENTMAPTYPE;
import main.renderer.material.Material.MAP;
import main.shader.Program;
import main.texture.Texture;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

public class MaterialFactory {

	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
	
	private Renderer renderer;
	public Map<String, Material> MATERIALS = new HashMap<>();

	private Material defaultMaterial;

	public MaterialFactory(Renderer renderer) {
		this.renderer = renderer;
		defaultMaterial = getMaterialWithoutRead(new MaterialInfo());
		
	}

	public Material getMaterial(MaterialInfo materialInfo) {
 	Material material = MATERIALS.get(materialInfo.name);
		if(material != null) {
			return material;
		}

		if(materialInfo.name == null || materialInfo.name == "") {
			materialInfo.name = "Material_" + count++;
		}
		
		material = read(Material.getDirectory() + materialInfo.name);
		
		if(material != null) {
			return material;
		}
		
		material = new Material();
		material.setMaterialInfo(materialInfo);
		//material.transparency = materialInfo.transparency;
		initMaterial(material);
		Material.write(material, materialInfo.name);
		return material;
	}

	public Material getMaterialWithoutRead(MaterialInfo materialInfo) {
		Material material = MATERIALS.get(materialInfo.name);
		if(material != null) {
			return material;
		}

		if(materialInfo.name == null || materialInfo.name == "") {
			materialInfo.name = "Material_" + count++;
		}
		
		material = new Material();
		material.setMaterialInfo(materialInfo);
		initMaterial(material);
		Material.write(material, materialInfo.name);
		return material;
	}

	public Material getMaterial(HashMap<MAP, String> hashMap) {
		return getMaterial("Material_" + MATERIALS.size(), hashMap);
	}

	public Material getMaterial(String name, HashMap<MAP, String> hashMap) {
		MaterialMap textures = new MaterialMap();
		
		for (MAP map : hashMap.keySet()) {
			try {
				textures.put(map, renderer.getTextureFactory().getTexture(hashMap.get(map)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		MaterialInfo info = new MaterialInfo(textures);
		info.name = name;
		Material material = getMaterial(info);
		return material;
	}

	private void initMaterial(Material material) {
		material.init(renderer);
		MATERIALS.put(material.getName(), material);
		material.initialized = true;
	}

	public Material get(String materialName) {
		Material material = MATERIALS.get(materialName);
		if(material == null) {
			material = read(materialName);

			if(material == null) {
				material = getDefaultMaterial();
			}
			MATERIALS.put(material.getName(), material);
		}
		return material;
	}
	
	public static final class MaterialInfo implements Serializable {
		public MaterialInfo(MaterialMap maps) {
			this.maps = maps;
		}

		public MaterialInfo setName(String name) {
			this.name = name;
			return this;
		}

		public MaterialInfo() {
		}

		public MaterialInfo(String name, MaterialMap maps) {
			this(maps);
		}

		public MaterialInfo(MaterialInfo materialInfo) {
			this.maps = materialInfo.maps;
			this.environmentMapType = materialInfo.environmentMapType;
			this.name = materialInfo.name;
			this.diffuse = materialInfo.diffuse;
			this.roughness = materialInfo.roughness;
			this.metallic = materialInfo.metallic;
			this.firstPassProgram = materialInfo.firstPassProgram;
			this.geometryShader = materialInfo.geometryShader;
			this.vertexShader = materialInfo.vertexShader;
			this.fragmentShader = materialInfo.fragmentShader;
		}

		public MaterialMap maps = new MaterialMap();
		public ENVIRONMENTMAPTYPE environmentMapType = ENVIRONMENTMAPTYPE.GENERATED;
		public String name = "";
		public Vector3f diffuse = new Vector3f();
		public float roughness = 0.75f;
		public float metallic = 0f;
		
		public boolean textureLess;
		transient public Program firstPassProgram;
		String geometryShader = "";
		String vertexShader = "";
		String fragmentShader = "";
		public void put(MAP map, Texture texture) {
			maps.put(map, texture);
		}

		public boolean hasCustomVertexShader() {
			return !vertexShader.equals("");
		}

		public boolean hasCustomFragmentShader() {
			return !fragmentShader.equals("");
		}

		public String getGeometryShader() {
			return geometryShader;
		}

		public void setGeometryShader(String geometryShader) {
			this.geometryShader = geometryShader;
		}

		public String getVertexShader() {
			return vertexShader;
		}

		public void setVertexShader(String vertexShader) {
			this.vertexShader = vertexShader;
		}

		public String getFragmentShader() {
			return fragmentShader;
		}

		public void setFragmentShader(String fragmentShader) {
			this.fragmentShader = fragmentShader;
		}
	}

	public void putAll(Map<String, MaterialInfo> materialLib) {
		for (String key : materialLib.keySet()) {
			MATERIALS.put(key, getMaterial(materialLib.get(key)));
		}
	}

	public Material getDefaultMaterial() {
		return defaultMaterial;
	}

    public Material read(String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
//			System.out.println(new File(Material.getDirectory() + fileName + ".hpmaterial").exists());
			fis = new FileInputStream(Material.getDirectory() + fileName + ".hpmaterial");
			in = new ObjectInputStream(fis);
			Material material = (Material) in.readObject();
			in.close();
			return getMaterialWithoutRead(material.getMaterialInfo());
//			return material;
		} catch (Exception e) {
//			e.printStackTrace();
			System.out.println("Material read (" + fileName + ") caused an exception, probably not very important");
		}
		return null;
	}

	public Map<String, Material> getMaterials() {
		return MATERIALS;
	}

	public List<Material> getMaterialsAsList() {
		List<Material> sortedList = new ArrayList<Material>(MATERIALS.values());
		sortedList.sort(new Comparator<Material>() {
			@Override
			public int compare(Material o1, Material o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		
		return sortedList;
	}
}
