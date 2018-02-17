package de.hanno.hpengine.engine.model.material;

import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.event.MaterialAddedEvent;
import de.hanno.hpengine.engine.event.bus.EventBus;
import de.hanno.hpengine.engine.model.material.Material.MAP;
import de.hanno.hpengine.engine.model.texture.TextureManager;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.model.material.Material.getDirectory;
import static de.hanno.hpengine.engine.model.material.Material.write;

public class MaterialManager {
    private static final Logger LOGGER = Logger.getLogger(MaterialManager.class.getName());
	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
	private final Material skyboxMaterial;

    public Map<String, Material> MATERIALS = new ConcurrentHashMap<>();

	private Material defaultMaterial;
	private TextureManager textureManager;

	public MaterialManager(Engine engine, TextureManager textureManager) {
		this.textureManager = textureManager;
		MaterialInfo defaultTemp = new MaterialInfo();
		defaultTemp.diffuse.x = (1.0f);
        defaultMaterial = getMaterial(defaultTemp, false);
		skyboxMaterial = getMaterial(new MaterialInfo().setName("skybox").setMaterialType(Material.MaterialType.UNLIT));

		if(Config.getInstance().isLoadDefaultMaterials()) {
		    initDefaultMaterials();
        }
		engine.getEventBus().register(this);
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
	    return getMaterial(materialInfo, true);
    }

	public Material getMaterial(MaterialInfo materialInfo, boolean readFromHdd) {
		if (materialInfo.name == null || "".equals(materialInfo.name)) {
			materialInfo.name = "Material_" + count++;
		}
		Supplier<Material> supplier = () -> {
			if (readFromHdd) {
				Material readMaterial = read(getDirectory() + materialInfo.name);
				if (readMaterial != null) {
					readMaterial.setMaterialIndex(MATERIALS.size());
					return readMaterial;
				}
			}
			Material newMaterial = new Material();
			newMaterial.setMaterialInfo(new MaterialInfo(materialInfo));
			newMaterial.init(this);
			newMaterial.setMaterialIndex(MATERIALS.size());

			write(newMaterial, materialInfo.name);

			EventBus.getInstance().post(new MaterialAddedEvent());
			return newMaterial;
		};
		addMaterial(materialInfo.name, supplier.get());
		return MATERIALS.get(materialInfo.name);
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
            textures.put(map, getTextureManager().getTexture(hashMap.get(map), srgba));
		}
		MaterialInfo info = new MaterialInfo(textures);
		info.name = name;
        return getMaterial(info);
	}

    public Material getMaterial(String materialName) {
		Supplier<Material> supplier = () -> {
			Material material = read(materialName);

			if (material == null) {
				material = getDefaultMaterial();
				Logger.getGlobal().info(() -> "Failed to get material " + materialName);
			} else {
				material.setMaterialIndex(MATERIALS.size());
			}
			EventBus.getInstance().post(new MaterialAddedEvent());
			return material;
		};
		addMaterial(materialName, supplier.get());
		return MATERIALS.get(materialName);
    }

	private void addMaterial(String key, Material material) {
	    MATERIALS.putIfAbsent(key, material);
    }
    public void putAll(Map<String, MaterialInfo> materialLib) {
		for (String key : materialLib.keySet()) {
			getMaterial(materialLib.get(key));
		}
	}

	public Material getDefaultMaterial() {
		return defaultMaterial;
	}

    public Material read(String resourceName) {
		String fileName = FilenameUtils.getBaseName(resourceName);
        String materialFileName = getDirectory() + fileName + ".hpmaterial";
        File materialFile = new File(materialFileName);
        if(materialFile.exists() && materialFile.isFile()) {

            try {
                FileInputStream fis = new FileInputStream(materialFileName);
                ObjectInputStream in = new ObjectInputStream(fis);
                Material material = (Material) in.readObject();
                in.close();
                material.init(this);
                return material;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                Logger.getGlobal().info("Material read (" + fileName + ") caused an exception, probably not very important");
            }
        }
		return null;
	}

	public List<Material> getMaterials() {
		return new ArrayList<>(MATERIALS.values());
	}

	public Material getSkyboxMaterial() {
		return skyboxMaterial;
	}

	public TextureManager getTextureManager() {
		return textureManager;
	}
}
