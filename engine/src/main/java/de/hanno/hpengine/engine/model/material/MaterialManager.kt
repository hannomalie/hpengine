package de.hanno.hpengine.engine.model.material

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.Material.MAP
import de.hanno.hpengine.engine.model.texture.TextureManager
import org.apache.commons.io.FilenameUtils

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.util.*
import java.util.logging.Logger

import de.hanno.hpengine.engine.model.material.Material.getDirectory
import de.hanno.hpengine.engine.model.material.Material.write
import de.hanno.hpengine.util.Util
import net.engio.mbassy.listener.Handler

class MaterialManager(private val engine: Engine, val textureManager: TextureManager) : Manager {
    val skyboxMaterial: Material
    private val eventBus: EventBus

    var MATERIALS: MutableMap<String, Material> = LinkedHashMap()

    val defaultMaterial: Material

    //		materials.sort(comparingInt(Material::getMaterialIndex));
    val materials: List<Material>
        get() = ArrayList(MATERIALS.values)

    val bufferMaterialsActionRef = engine.renderManager.renderState.registerAction({ renderState ->
        engine.gpuContext.execute {
            val materials = engine.getScene().materialManager.materials
            renderState.entitiesState.materialBuffer.put(*Util.toArray(materials, Material::class.java))
            renderState.entitiesState.materialBuffer.buffer.position(0)
            //            for(Material material: materials) {
            //                System.out.println("Material: " + material.getName() + "(" + material.getMaterialIndex() + ")");
            //                Material.debugPrintFromBufferStatic(entitiesState.materialBuffer.getBuffer());
            //            }
        }
    })

    init {
        this.eventBus = engine.eventBus
        val defaultTemp = MaterialInfo()
        defaultTemp.setName("default")
        defaultTemp.diffuse.x = 1.0f
        defaultMaterial = getMaterial(defaultTemp, false)
        skyboxMaterial = getMaterial(MaterialInfo().setName("skybox").setMaterialType(Material.MaterialType.UNLIT))

        if (Config.getInstance().isLoadDefaultMaterials) {
            initDefaultMaterials()
        }
        engine.eventBus.register(this)
    }

    fun initDefaultMaterials() {

        getMaterial("default", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/default.dds")
            }
        })

        getMaterial("stone", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/stone_diffuse.png")
                put(MAP.NORMAL, "hp/assets/textures/stone_normal.png")
            }
        })

        getMaterial("stone2", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/brick.png")
                put(MAP.NORMAL, "hp/assets/textures/brick_normal.png")
            }
        })

        getMaterial("wood", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/wood_diffuse.png")
                put(MAP.NORMAL, "hp/assets/textures/wood_normal.png")
            }
        })
        getMaterial("stoneWet", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/stone_diffuse.png")
                put(MAP.NORMAL, "hp/assets/textures/stone_normal.png")
                put(MAP.REFLECTION, "hp/assets/textures/stone_reflection.png")
            }
        })
        getMaterial("mirror", object : HashMap<MAP, String>() {
            init {
                put(MAP.REFLECTION, "hp/assets/textures/default.dds")
            }
        })
        getMaterial("stoneParallax", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/models/textures/bricks_parallax.jpg")
                put(MAP.HEIGHT, "hp/assets/models/textures/bricks_parallax_height.jpg")
                put(MAP.NORMAL, "hp/assets/models/textures/bricks_parallax_normal.png")
            }
        })
    }

    @JvmOverloads
    fun getMaterial(materialInfo: MaterialInfo, readFromHdd: Boolean = true): Material {
        if (materialInfo.name == null || "" == materialInfo.name) {
            throw IllegalArgumentException("Don't pass a material with null or empty name")
        }
        fun readOrCreateMaterial(): Material {
            if (readFromHdd) {
                val readMaterial = read(getDirectory() + materialInfo.name)
                if (readMaterial != null) {
                    readMaterial.materialIndex = MATERIALS.size
                    return readMaterial
                }
            }
            val newMaterial = Material()
            newMaterial.materialInfo = MaterialInfo(materialInfo)
            newMaterial.init(this)
            newMaterial.materialIndex = MATERIALS.size

            write(newMaterial, materialInfo.name)

            return newMaterial
        }
        addMaterial(materialInfo.name, readOrCreateMaterial())
        return MATERIALS[materialInfo.name]!!
    }

    fun getMaterial(hashMap: HashMap<MAP, String>): Material {
        return getMaterial("Material_" + MATERIALS.size, hashMap)
    }

    fun getMaterial(name: String, hashMap: HashMap<MAP, String>): Material {
        val textures = MaterialMap()

        for (map in hashMap.keys) {
            var srgba = false
            if (map == MAP.DIFFUSE) {
                srgba = true
            }
            textures.put(map, textureManager.getTexture(hashMap[map], srgba))
        }
        val info = MaterialInfo(textures)
        info.name = name
        return getMaterial(info)
    }

    fun getMaterial(materialName: String): Material {
        fun supplier(): Material {
            var material = read(materialName)

            if (material == null) {
                material = defaultMaterial
                Logger.getGlobal().info { "Failed to get material " + materialName }
            } else {
                material.materialIndex = MATERIALS.size
            }
            return material
        }
        addMaterial(materialName, supplier())
        return MATERIALS[materialName]!!
    }

    private fun addMaterial(key: String, material: Material) {
        MATERIALS.putIfAbsent(key, material)
        eventBus.post(MaterialAddedEvent())
    }

    fun putAll(materialLib: Map<String, MaterialInfo>) {
        for (key in materialLib.keys) {
            getMaterial(materialLib[key]!!)
        }
    }

    fun read(resourceName: String): Material? {
        val fileName = FilenameUtils.getBaseName(resourceName)
        val materialFileName = getDirectory() + fileName + ".hpmaterial"
        val materialFile = File(materialFileName)
        if (materialFile.exists() && materialFile.isFile) {

            try {
                val fis = FileInputStream(materialFileName)
                val `in` = ObjectInputStream(fis)
                val material = `in`.readObject() as Material
                `in`.close()
                material.init(this)
                return material
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                Logger.getGlobal().info("Material read ($fileName) caused an exception, probably not very important")
            }

        }
        return null
    }

    override fun clear() {

    }

    override fun update(deltaSeconds: Float) {

    }

    @Subscribe
    @Handler
    fun handle(event: MaterialAddedEvent) {
        bufferMaterialsActionRef.request(engine.renderManager.drawCycle.get())
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialChangedEvent) {
        if (event.material.isPresent) {
            //                renderStateX.bufferMaterial(event.getMaterials().get());
            bufferMaterialsActionRef.request(engine.renderManager.drawCycle.get())
        } else {
            bufferMaterialsActionRef.request(engine.renderManager.drawCycle.get())
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(MaterialManager::class.java.name)
        val TEXTUREASSETSPATH = "assets/textures/"
        var count = 0
    }
}
