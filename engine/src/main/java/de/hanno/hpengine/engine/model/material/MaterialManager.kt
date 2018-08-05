package de.hanno.hpengine.engine.model.material

import com.google.common.eventbus.Subscribe
import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.event.TexturesChangedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.util.commandqueue.FutureCallable
import kotlinx.collections.immutable.toImmutableHashMap
import net.engio.mbassy.listener.Handler
import org.apache.commons.io.FilenameUtils
import org.joml.Vector3f
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Supplier
import java.util.logging.Logger
import kotlin.collections.ArrayList

class MaterialManager(private val engine: Engine, val textureManager: TextureManager) : Manager {
    val skyboxMaterial: SimpleMaterial
    private val eventBus: EventBus = engine.eventBus

    var MATERIALS: MutableMap<String, SimpleMaterial> = LinkedHashMap()

    val defaultMaterial: SimpleMaterial

    val materials: List<SimpleMaterial>
        get() = ArrayList(MATERIALS.values)

    val bufferMaterialsExtractor = Supplier {
        engine.commandQueue.calculate(object: FutureCallable<List<SimpleMaterial>>() {
            override fun execute(): List<SimpleMaterial> {
                return materials // TODO: Move this out of the update queue
            }
        })
    }
    val bufferMaterialsConsumer = BiConsumer<RenderState, List<SimpleMaterial>> { renderState, materials ->
        renderState.entitiesState.materialBuffer.setCapacityInBytes(SimpleMaterial.bytesPerObject * materials.size)
        renderState.entitiesState.materialBuffer.buffer.rewind()
        for (material in materials) {
            material.putToBuffer(renderState.materialBuffer.buffer)
        }
    }
    val bufferMaterialsActionRef = engine.renderManager.renderState.registerAction(TripleBuffer.RareAction<List<SimpleMaterial>>(bufferMaterialsExtractor, bufferMaterialsConsumer, engine))

    init {
        defaultMaterial = getMaterial(SimpleMaterialInfo("default", diffuse = Vector3f(1f,0f,0f)), false)
        skyboxMaterial = getMaterial(SimpleMaterialInfo("skybox", materialType = SimpleMaterial.MaterialType.UNLIT))

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
                put(MAP.HEIGHT, "hp/assets/textures/stone_height.png")
            }
        })

        getMaterial("stone2", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/brick.png")
                put(MAP.NORMAL, "hp/assets/textures/brick_normal.png")
            }
        })

        getMaterial("brick", object : HashMap<MAP, String>() {
            init {
                put(MAP.DIFFUSE, "hp/assets/textures/brick.png")
                put(MAP.NORMAL, "hp/assets/textures/brick_normal.png")
                put(MAP.HEIGHT, "hp/assets/textures/brick_height.png")
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
                put(MAP.DIFFUSE, "hp/assets/textures/bricks_parallax.dds")
                put(MAP.HEIGHT, "hp/assets/textures/bricks_parallax_height.dds")
                put(MAP.NORMAL, "hp/assets/textures/bricks_parallax_normal.dds")
            }
        })
    }

    @JvmOverloads
    fun getMaterial(materialInfo: SimpleMaterialInfo, readFromHdd: Boolean = false): SimpleMaterial {
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
        val textures = hashMapOf<MAP, Texture>()

        hashMap.forEach { map, value ->
            textures[map] = textureManager.getTexture(value, map == MAP.DIFFUSE)
        }
        val info = SimpleMaterialInfo(name = name, mapsInternal = textures.toImmutableHashMap())
        return getMaterial(info)
    }

    fun getMaterial(materialName: String): SimpleMaterial {
        fun supplier(): SimpleMaterial {
            var material = read(materialName)

            if (material == null) {
                material = defaultMaterial
                Logger.getGlobal().info { "Failed to get material " + materialName }
            } else {
                material.materialIndex = MATERIALS.size
            }
            return material
        }
//        addMaterial(materialName, supplier())

        return MATERIALS[materialName] ?: return defaultMaterial
    }

    private fun addMaterial(key: String, material: SimpleMaterial) {
        MATERIALS[key] = material
        eventBus.post(MaterialAddedEvent())
    }

    fun putAll(materialLib: Map<String, SimpleMaterialInfo>) {
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
        bufferMaterialsActionRef.request(engine.renderManager.drawCycle.get())
    }

    @Subscribe
    @Handler
    fun handle(event: TexturesChangedEvent) {
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

        fun getDirectory(): String {
            return DirectoryManager.WORKDIR_NAME + "/assets/materials/"
        }
    }

    fun changeMaterial(changedMaterial: SimpleMaterialInfo) {
        val oldMaterial = materials.find { it.materialInfo.name == changedMaterial.name }
        if(oldMaterial != null) {
            oldMaterial.materialInfo = changedMaterial
//            MATERIALS.remove(oldMaterial.name)
//            getMaterial(changedMaterial)
        }
        bufferMaterialsActionRef.request(engine.renderManager.drawCycle.get())
        eventBus.post(MaterialChangedEvent())
    }
}
