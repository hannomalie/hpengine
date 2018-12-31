package de.hanno.hpengine.engine.model.material

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.event.TexturesChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.struct.StaticStructObjectArray
import de.hanno.struct.copyTo
import net.engio.mbassy.listener.Handler
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.util.*
import java.util.logging.Logger
import kotlin.collections.ArrayList

class MaterialManager(private val backend: Backend, val textureManager: TextureManager = backend.textureManager) : Manager {
    val skyboxMaterial: SimpleMaterial
    private val eventBus: EventBus = backend.eventBus

    var MATERIALS: MutableMap<String, SimpleMaterial> = LinkedHashMap()

    val defaultMaterial: SimpleMaterial

    val materials: List<SimpleMaterial>
        get() = ArrayList(MATERIALS.values)

    val materialsAsStructs = StaticStructObjectArray(null, 1000) { MaterialStruct(it) }

    init {
        defaultMaterial = getMaterial(SimpleMaterialInfo(name = "default", diffuse = Vector3f(1f,0f,0f)).apply {
          put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/default.dds", true))
        })
        skyboxMaterial = getMaterial(SimpleMaterialInfo("skybox", materialType = SimpleMaterial.MaterialType.UNLIT))

        if (Config.getInstance().isLoadDefaultMaterials) {
            initDefaultMaterials()
        }
        backend.eventBus.register(this)
    }

    fun initDefaultMaterials() {

        getMaterial(SimpleMaterialInfo("stone").apply {
            put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/stone_diffuse.png", true))
            put(MAP.NORMAL, backend.textureManager.getTexture("hp/assets/textures/stone_normal.png"))
            put(MAP.HEIGHT, backend.textureManager.getTexture("hp/assets/textures/stone_height.png"))
        })

//        getMaterial(SimpleMaterialInfo("stone2").apply {
//            put(MAP.DIFFUSE, managerContext.textureManager.getTexture("hp/assets/textures/brick.png", true))
//            put(MAP.NORMAL, managerContext.textureManager.getTexture("hp/assets/textures/brick_normal.png"))
//        })

        getMaterial(SimpleMaterialInfo("brick").apply {
            put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/brick.png", true))
            put(MAP.NORMAL, backend.textureManager.getTexture("hp/assets/textures/brick_normal.png"))
            put(MAP.HEIGHT, backend.textureManager.getTexture("hp/assets/textures/brick_height.png"))
        })

        getMaterial(SimpleMaterialInfo("wood").apply {
            put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/wood_diffuse.png", true))
            put(MAP.NORMAL, backend.textureManager.getTexture("hp/assets/textures/wood_normal.png"))
        })

        getMaterial(SimpleMaterialInfo("stoneWet").apply {
            put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/stone_diffuse.png", true))
            put(MAP.NORMAL, backend.textureManager.getTexture("hp/assets/textures/stone_normal.png"))
            put(MAP.REFLECTION, backend.textureManager.getTexture("hp/assets/textures/stone_reflection.png"))
        })
        getMaterial(SimpleMaterialInfo(name = "mirror", diffuse = Vector3f(1f,1f,1f), metallic = 1f))

        getMaterial(SimpleMaterialInfo("stoneWet").apply {
            put(MAP.DIFFUSE, backend.textureManager.getTexture("hp/assets/textures/bricks_parallax.dds", true))
            put(MAP.HEIGHT, backend.textureManager.getTexture("hp/assets/textures/bricks_parallax_height.dds"))
            put(MAP.NORMAL, backend.textureManager.getTexture("hp/assets/textures/bricks_parallax_normal.dds"))
        })
    }

    @JvmOverloads
    fun getMaterial(materialInfo: MaterialInfo, readFromHdd: Boolean = false): SimpleMaterial {
        if ("" == materialInfo.name) {
            throw IllegalArgumentException("Don't pass a material with null or empty name")
        }
        fun readOrCreateMaterial(): SimpleMaterial {
            if (readFromHdd) {
                val readMaterial = read(getDirectory() + materialInfo.name)
                if (readMaterial != null) {
                    readMaterial.materialIndex = MATERIALS.size
                    return readMaterial
                }
            }
            val newMaterial = SimpleMaterial(materialInfo)
            newMaterial.init(this)
            newMaterial.materialIndex = MATERIALS.size

//            TODO: Reactivate this? ImmutableHashMap is not Serializable, in SimpleMaterialInfo
//            write(newMaterial, materialInfo.name)

            return newMaterial
        }
        addMaterial(materialInfo.name, readOrCreateMaterial())
        return MATERIALS[materialInfo.name]!!
    }

    fun getMaterial(hashMap: HashMap<MAP, String>): SimpleMaterial {
        return getMaterial("Material_" + MATERIALS.size, hashMap)
    }

    fun getMaterial(name: String, hashMap: HashMap<MAP, String>): SimpleMaterial {
        val textures = mutableMapOf<MAP, Texture<TextureDimension2D>>()

        hashMap.forEach { map, value ->
            textures[map] = textureManager.getTexture(value, map == MAP.DIFFUSE)
        }
        val info = SimpleMaterialInfo(name = name, mapsInternal = textures)
        return getMaterial(info)
    }

    fun getMaterial(materialName: String): SimpleMaterial {
        return MATERIALS[materialName] ?: return defaultMaterial
    }

    private fun addMaterial(key: String, material: SimpleMaterial) {
        MATERIALS[key] = material
        eventBus.post(MaterialAddedEvent())
    }

    fun putAll(materialLib: Map<String, MaterialInfo>) {
        for (key in materialLib.keys) {
            getMaterial(materialLib[key]!!)
        }
    }

    fun read(resourceName: String): SimpleMaterial? {
        val fileName = FilenameUtils.getBaseName(resourceName)
        val materialFileName = getDirectory() + fileName + ".hpmaterial"
        val materialFile = File(materialFileName)
        if (materialFile.exists() && materialFile.isFile) {

            try {
                val fis = FileInputStream(materialFileName)
                val `in` = ObjectInputStream(fis)
                val material = `in`.readObject() as SimpleMaterial
                `in`.close()
                material.init(this)
                return material
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                Logger.getGlobal().info("SimpleMaterial read ($fileName) caused an exception, probably not very important")
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
        requestMaterialBuffering()
    }

    @Subscribe
    @Handler
    fun handle(event: TexturesChangedEvent) {
        requestMaterialBuffering()
    }

    @Subscribe
    @Handler
    fun handle(event: MaterialChangedEvent) {
        if (event.material.isPresent) {
            requestMaterialBuffering()
        } else {
            requestMaterialBuffering()
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(MaterialManager::class.java.name)
        val TEXTUREASSETSPATH = "assets/textures/"
        var count = 0

        fun getDirectory(): String {
            return DirectoryManager.WORKDIR_NAME + "/assets/materials/"
        }
    }

    fun changeMaterial(changedMaterial: MaterialInfo) {
        val oldMaterial = materials.find { it.materialInfo.name == changedMaterial.name }
        if(oldMaterial != null) {
            oldMaterial.materialInfo = changedMaterial
//            MATERIALS.remove(oldMaterial.name)
//            getMaterial(changedMaterial)
        }
        requestMaterialBuffering()
        eventBus.post(MaterialChangedEvent())
    }

    private fun requestMaterialBuffering() {
//        bufferMaterialsActionRef.request(renderManager.drawCycle.get())
    }

    override fun extract(renderState: RenderState) {
//        TODO: Remove most of this
        renderState.entitiesState.materialBuffer.setCapacityInBytes(SimpleMaterial.bytesPerObject * materials.size)
        renderState.entitiesState.materialBuffer.buffer.rewind()
        for ((index, material) in materials.withIndex()) {
//            material.putToBuffer(renderState.materialBuffer.buffer)
            val target = materialsAsStructs[index]
            target.diffuse.set(material.materialInfo.diffuse)
            target.metallic = material.materialInfo.metallic
            target.roughness = material.materialInfo.roughness
            target.ambient = material.materialInfo.ambient
            target.parallaxBias = material.materialInfo.parallaxBias
            target.parallaxScale = material.materialInfo.parallaxScale
            target.transparency = material.materialInfo.transparency
            target.materialType = material.materialInfo.materialType
            target.diffuseMapHandle = material.materialInfo.maps[MAP.DIFFUSE]?.handle ?: 0
            target.normalMapHandle = material.materialInfo.maps[MAP.NORMAL]?.handle ?: 0
            target.specularMapHandle = material.materialInfo.maps[MAP.SPECULAR]?.handle ?: 0
            target.heightMapHandle = material.materialInfo.maps[MAP.HEIGHT]?.handle ?: 0
            target.occlusionMapHandle = material.materialInfo.maps[MAP.OCCLUSION]?.handle ?: 0
            target.roughnessMapHandle = material.materialInfo.maps[MAP.ROUGHNESS]?.handle ?: 0
        }
        materialsAsStructs.buffer.copyTo(renderState.entitiesState.materialBuffer.buffer)
        renderState.skyBoxMaterialIndex = skyboxMaterial.materialIndex
    }

}
