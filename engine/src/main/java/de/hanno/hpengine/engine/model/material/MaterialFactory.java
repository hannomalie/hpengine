package de.hanno.hpengine.engine.model.material;

import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.event.MaterialAddedEvent;
import de.hanno.hpengine.engine.event.bus.EventBus;
import de.hanno.hpengine.engine.model.material.Material.MAP;
import de.hanno.hpengine.engine.model.texture.TextureFactory;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.logging.Logger;

import static de.hanno.hpengine.engine.model.material.Material.getDirectory;
import static de.hanno.hpengine.engine.model.material.Material.write;

public class MaterialFactory {
    private static final Logger LOGGER = Logger.getLogger(MaterialFactory.class.getName());
	public static final String TEXTUREASSETSPATH = "assets/textures/";
	public static int count = 0;
    private static MaterialFactory instance;
	private final Material skyboxMaterial;

    public ListWithSyncedAdder<Material> MATERIALS = new ListWithSyncedAdder();

	private Material defaultMaterial;
	private TextureFactory textureFactory;

	public MaterialFactory(TextureFactory textureFactory) {
		this.textureFactory = textureFactory;
		MaterialInfo defaultTemp = new MaterialInfo();
		defaultTemp.diffuse.x = (1.0f);
        defaultMaterial = getMaterial(defaultTemp, false);
		skyboxMaterial = getMaterial(new MaterialInfo().setName("skybox").setMaterialType(Material.MaterialType.UNLIT));

		if(Config.getInstance().isLoadDefaultMaterials()) {
		    initDefaultMaterials();
        }
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
	    return getMaterial(materialInfo, true);
    }

	public Material getMaterial(MaterialInfo materialInfo, boolean readFromHdd) {
		if (materialInfo.name == null || materialInfo.name == "") {
			materialInfo.name = "Material_" + count++;
		}
		return MATERIALS.addIfAbsent(() -> {
			if (readFromHdd) {
				Material readMaterial = read(getDirectory() + materialInfo.name);
				if (readMaterial != null) {
					return readMaterial;
				}
			}
			Material newMaterial = new Material();
			newMaterial.setMaterialInfo(new MaterialInfo(materialInfo));
			newMaterial.init(this);

			write(newMaterial, materialInfo.name);

			EventBus.getInstance().post(new MaterialAddedEvent());
			return newMaterial;
		});
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
            textures.put(map, getTextureFactory().getTexture(hashMap.get(map), srgba));
		}
		MaterialInfo info = new MaterialInfo(textures);
		info.name = name;
        return getMaterial(info);
	}

    public Material getMaterial(String materialName) {
        return MATERIALS.addIfAbsent(() -> {
            Material material = read(materialName);

            if(material == null) {
                material = getDefaultMaterial();
                Logger.getGlobal().info(() -> "Failed to get material " + materialName);
            }
            EventBus.getInstance().post(new MaterialAddedEvent());
            return material;
        });
    }

	private void addMaterial(String key, Material material) {
	    if(MATERIALS.contains(material)) {
            Logger.getGlobal().warning(() -> "Material already defined: " + key);
            return;
        }
        MATERIALS.add(material);
    }
    private void addMaterial(Material material) {
        addMaterial(material.getName(), material);
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
                handleEvolution(material.getMaterialInfo());
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

    private void handleEvolution(MaterialInfo materialInfo) {
    	
    }

	public ListWithSyncedAdder<Material> getMaterials() {
		return MATERIALS;
	}

	public List<Material> getMaterialsAsList() {
		return getMaterials();
	}

	public int indexOf(Material material) {
		return MATERIALS.indexOf(material);
	}

	public Material getSkyboxMaterial() {
		return skyboxMaterial;
	}

	public TextureFactory getTextureFactory() {
		return textureFactory;
	}
}
