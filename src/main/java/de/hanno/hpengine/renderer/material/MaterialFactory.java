package de.hanno.hpengine.renderer.material;

import com.google.common.eventbus.Subscribe;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.event.MaterialAddedEvent;
import de.hanno.hpengine.event.MaterialChangedEvent;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FilenameUtils;
import de.hanno.hpengine.renderer.OpenGLContext;
import de.hanno.hpengine.renderer.material.Material.MAP;
import de.hanno.hpengine.shader.OpenGLBuffer;
import de.hanno.hpengine.shader.PersistentMappedBuffer;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.Util;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static de.hanno.hpengine.renderer.material.Material.getDirectory;
import static de.hanno.hpengine.renderer.material.Material.write;

public class MaterialFactory {
    private static final Logger LOGGER = Logger.getLogger(MaterialFactory.class.getName());
	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
    private static MaterialFactory instance;

    public static MaterialFactory getInstance() {
        if(instance == null) {
            throw new IllegalStateException("Call Engine.init() before using it");
        }
        return instance;
    }
    public static void init() {
        instance = new MaterialFactory();
    }

	public Map<String, Material> MATERIALS = new ConcurrentHashMap<>();

	private Material defaultMaterial;

	private MaterialFactory() {
		MaterialInfo defaultTemp = new MaterialInfo();
		defaultTemp.diffuse.setX(1.0f);
		defaultMaterial = getMaterialWithoutRead(defaultTemp);

		Engine.getEventBus().register(this);
	}

	public void initDefaultMaterials() {

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
			return material;
		}
		
		material = new Material();
		material.setMaterialInfo(new MaterialInfo(materialInfo));
		initMaterial(material);
		write(material, materialInfo.name);
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
        addMaterial(material);
        material.initialized = true;
	}

    private void addMaterial(String key, Material material) {
        MATERIALS.put(key, material);
    }
    private void addMaterial(Material material) {
        addMaterial(material.getName(), material);
    }

    public Material get(String materialName) {
		Material material = MATERIALS.get(materialName);
		if(material == null) {
			material = read(materialName);

			if(material == null) {
				material = getDefaultMaterial();
				Logger.getGlobal().info("Failed to get material " + materialName);
			}
            addMaterial(material);
        }
		return material;
	}

    public void putAll(Map<String, MaterialInfo> materialLib) {
		for (String key : materialLib.keySet()) {
			addMaterial(key, getMaterial(materialLib.get(key)));
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
			LOGGER.info(String.valueOf(new File(Material.getDirectory() + fileName + ".hpmaterial").exists()));
			fis = new FileInputStream(getDirectory() + fileName + ".hpmaterial");
			in = new ObjectInputStream(fis);
			Material material = (Material) in.readObject();
			in.close();
			handleEvolution(material.getMaterialInfo());
            Material materialWithoutRead = getMaterialWithoutRead(material.getMaterialInfo());
            return materialWithoutRead;
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
		return new ArrayList<>(MATERIALS.values()).indexOf(material);
	}

}
