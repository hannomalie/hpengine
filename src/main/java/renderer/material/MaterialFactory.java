package renderer.material;

import com.google.common.eventbus.Subscribe;
import engine.AppContext;
import event.MaterialAddedEvent;
import event.MaterialChangedEvent;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.material.Material.ENVIRONMENTMAPTYPE;
import renderer.material.Material.MAP;
import renderer.material.Material.MaterialType;
import shader.OpenGLBuffer;
import shader.PersistentMappedStorageBuffer;
import shader.StorageBuffer;
import texture.Texture;
import texture.TextureFactory;
import util.Util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static renderer.material.Material.MaterialType.DEFAULT;
import static renderer.material.Material.getDirectory;
import static renderer.material.Material.write;

public class MaterialFactory {
	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
    private static MaterialFactory instance;

    public static MaterialFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call AppContext.init() before using it");
        }
        return instance;
    }
    public static void init() {
        instance = new MaterialFactory();
    }

	public Map<String, Material> MATERIALS = new ConcurrentHashMap<>();

	private Material defaultMaterial;

	private OpenGLBuffer materialBuffer;

	private MaterialFactory() {
//		materialBuffer = OpenGLContext.getInstance().calculateWithOpenGLContext(() -> new StorageBuffer(20000));
        materialBuffer = new PersistentMappedStorageBuffer(20000);

		MaterialInfo defaultTemp = new MaterialInfo();
		defaultTemp.diffuse.setX(1.0f);
		defaultMaterial = getMaterialWithoutRead(defaultTemp);
		initDefaultMaterials();

		AppContext.getEventBus().register(this);
	}

	private void initDefaultMaterials() {

		getMaterial("default", new HashMap<MAP, String>() {{
			put(MAP.DIFFUSE, "hp/assets/textures/default.dds");
		}});

		getMaterial("stone", new HashMap<MAP, String>() {{
			put(MAP.DIFFUSE, "hp/assets/textures/stone_diffuse.png");
			put(MAP.NORMAL, "hp/assets/textures/stone_normal.png");
		}});

		getMaterial("stone2", new HashMap<MAP, String>() {{
			put(MAP.DIFFUSE, "hp/assets/textures/brick.png");
			put(MAP.NORMAL, "hp/assets/textures/brick_normal.png");
		}});

		getMaterial("wood", new HashMap<MAP, String>() {{
            put(MAP.DIFFUSE, "hp/assets/textures/wood_diffuse.png");
            put(MAP.NORMAL, "hp/assets/textures/wood_normal.png");
        }});
		getMaterial("stoneWet", new HashMap<MAP, String>() {{
			put(MAP.DIFFUSE, "hp/assets/textures/stone_diffuse.png");
			put(MAP.NORMAL, "hp/assets/textures/stone_normal.png");
			put(MAP.REFLECTION, "hp/assets/textures/stone_reflection.png");
		}});
		getMaterial("mirror", new HashMap<MAP, String>() {{
            put(MAP.REFLECTION, "hp/assets/textures/default.dds");
        }});
		getMaterial("stoneParallax", new HashMap<MAP, String>() {{
            put(MAP.DIFFUSE, "hp/assets/models/textures/bricks_parallax.jpg");
            put(MAP.HEIGHT, "hp/assets/models/textures/bricks_parallax_height.jpg");
            put(MAP.NORMAL, "hp/assets/models/textures/bricks_parallax_normal.png");
        }});
	}

	public Material getMaterial(MaterialInfo materialInfo) {
 		Material material = MATERIALS.get(materialInfo.name);
		if(material != null) {
			return material;
		}

		if(materialInfo.name == null || materialInfo.name == "") {
			materialInfo.name = "Material_" + count++;
		}
		
		material = read(getDirectory() + materialInfo.name);
		
		if(material != null) {
			AppContext.getEventBus().post(new MaterialAddedEvent());
			return material;
		}
		
		material = new Material();
		material.setMaterialInfo(new MaterialInfo(materialInfo));
		initMaterial(material);
		write(material, materialInfo.name);
		AppContext.getEventBus().post(new MaterialAddedEvent());
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
		write(material, materialInfo.name);
		return material;
	}

	public Material getMaterial(HashMap<MAP, String> hashMap) {
		return getMaterial("Material_" + MATERIALS.size(), hashMap);
	}

	public Material getMaterial(String name, HashMap<MAP, String> hashMap) {
		MaterialMap textures = new MaterialMap();
		
		for (MAP map : hashMap.keySet()) {
			boolean srgba = false;
			if(map.equals(MAP.DIFFUSE)) {
				srgba = true;
			}
			textures.put(map, TextureFactory.getInstance().getTexture(hashMap.get(map), srgba));
		}
		MaterialInfo info = new MaterialInfo(textures);
		info.name = name;
		Material material = getMaterial(info);
		return material;
	}

	private void initMaterial(Material material) {
		material.init();
		MATERIALS.put(material.getName(), material);
		material.initialized = true;
	}

	public Material get(String materialName) {
		Material material = MATERIALS.get(materialName);
		if(material == null) {
			material = read(materialName);

			if(material == null) {
				material = getDefaultMaterial();
				Logger.getGlobal().info("Failed to get material " + materialName);
			}
			MATERIALS.put(material.getName(), material);
		}
		return material;
	}

    public OpenGLBuffer getMaterialBuffer() {
        return materialBuffer;
    }

    public static final class MaterialInfo implements Serializable {
		private static final long serialVersionUID = 3564429930446909410L;

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
			this.diffuse = new Vector3f(materialInfo.diffuse);
			this.roughness = materialInfo.roughness;
			this.metallic = materialInfo.metallic;
			this.ambient = materialInfo.ambient;
			this.transparency = materialInfo.transparency;
			this.parallaxScale = materialInfo.parallaxScale;
			this.parallaxBias = materialInfo.parallaxBias;
			this.materialType = materialInfo.materialType;
		}

		public MaterialMap maps = new MaterialMap();
		public ENVIRONMENTMAPTYPE environmentMapType = ENVIRONMENTMAPTYPE.GENERATED;
		public String name = "";
		public Vector3f diffuse = new Vector3f();
		public float roughness = 0.95f;
		public float metallic = 0f;
		public float ambient = 0;
		public float transparency = 0;
		public float parallaxScale = 0.04f;
		public float parallaxBias = 0.02f;
		public MaterialType materialType = DEFAULT;
		
		public boolean textureLess;
		public void put(MAP map, Texture texture) {
			maps.put(map, texture);
		}

		public MaterialInfo setRoughness(float roughness) {
			this.roughness = roughness;
			return this;
		}

		public MaterialInfo setMetallic(float metallic) {
			this.metallic = metallic;
			return this;
		}

		public MaterialInfo setDiffuse(Vector3f diffuse) {
			this.diffuse.set(diffuse);
			return this;
		}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MaterialInfo that = (MaterialInfo) o;

            if (Float.compare(that.roughness, roughness) != 0) return false;
            if (Float.compare(that.metallic, metallic) != 0) return false;
            if (Float.compare(that.ambient, ambient) != 0) return false;
            if (Float.compare(that.transparency, transparency) != 0) return false;
            if (Float.compare(that.parallaxScale, parallaxScale) != 0) return false;
            if (Float.compare(that.parallaxBias, parallaxBias) != 0) return false;
            if (textureLess != that.textureLess) return false;
            if (maps != null ? !maps.equals(that.maps) : that.maps != null) return false;
            if (environmentMapType != that.environmentMapType) return false;
            if (name != null ? !name.equals(that.name) : that.name != null) return false;
            if (diffuse != null ? !diffuse.equals(that.diffuse) : that.diffuse != null) return false;
            return materialType == that.materialType;

        }

        @Override
        public int hashCode() {
            int result = maps != null ? maps.hashCode() : 0;
            result = 31 * result + (environmentMapType != null ? environmentMapType.hashCode() : 0);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + (diffuse != null ? diffuse.hashCode() : 0);
            result = 31 * result + (roughness != +0.0f ? Float.floatToIntBits(roughness) : 0);
            result = 31 * result + (metallic != +0.0f ? Float.floatToIntBits(metallic) : 0);
            result = 31 * result + (ambient != +0.0f ? Float.floatToIntBits(ambient) : 0);
            result = 31 * result + (transparency != +0.0f ? Float.floatToIntBits(transparency) : 0);
            result = 31 * result + (parallaxScale != +0.0f ? Float.floatToIntBits(parallaxScale) : 0);
            result = 31 * result + (parallaxBias != +0.0f ? Float.floatToIntBits(parallaxBias) : 0);
            result = 31 * result + (materialType != null ? materialType.hashCode() : 0);
            result = 31 * result + (textureLess ? 1 : 0);
            return result;
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
			fis = new FileInputStream(getDirectory() + fileName + ".hpmaterial");
			in = new ObjectInputStream(fis);
			Material material = (Material) in.readObject();
			in.close();
			handleEvolution(material.getMaterialInfo());
			return getMaterialWithoutRead(material.getMaterialInfo());
//			return material;
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
//			e.printStackTrace();
			Logger.getGlobal().info("Material read (" + fileName + ") caused an exception, probably not very important");
		}
		return null;
	}

    private void handleEvolution(MaterialInfo materialInfo) {
    	
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

	public int indexOf(Material material) {
		return new ArrayList<Material>(MATERIALS.values()).indexOf(material);
	}

	@Subscribe
	public void bufferMaterials(MaterialAddedEvent event) {
		OpenGLContext.getInstance().doWithOpenGLContext(() -> {
            materialBuffer.put(Util.toArray(MATERIALS.values(), Material.class));
        });
	}
	@Subscribe
	public void bufferMaterials(MaterialChangedEvent event) {
		OpenGLContext.getInstance().doWithOpenGLContext(() -> {
            ArrayList<Material> materials = new ArrayList<Material>(getMaterials().values());
			materialBuffer.put(Util.toArray(MATERIALS.values(), Material.class));

//            DoubleBuffer temp = materialBuffer.getValues();
//            for(int i = 0; i < materials.size()*16; i++) {
//
//                int index = i + 1;
//
//                if(index%14 == 0) {
//                    System.out.print(Double.doubleToRawLongBits(temp.get(i)));
//                } else if(index%15 == 0) {
//                    System.out.print(Double.doubleToRawLongBits(temp.get(i)));
//                } else if(index%16 == 0) {
//                    System.out.print(Double.doubleToRawLongBits(temp.get(i)));
//                } else {
//                    System.out.print(temp.get(i));
//                }
//                System.out.print(" ");
//
//                if(index%16 == 0) {
//                    System.out.print(" (index " + ((i/15)-1) + ") ");
//                    System.out.print(materials.get((i / 15) - 1 ).getName());
//                    System.out.println();
//                }
//            }
		});
	}
}
