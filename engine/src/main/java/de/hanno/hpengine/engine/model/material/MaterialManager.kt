package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.event.MaterialAddedEvent
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.resize
import org.joml.Vector3f
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.logging.Logger

class MaterialManager(val config: Config,
                      private val eventBus: EventBus,
                      val textureManager: TextureManager,
                      val singleThreadContext: AddResourceContext) : Manager {

    constructor(engineContext: EngineContext<*>,
                config: Config = engineContext.config,
                eventBus: EventBus = engineContext.eventBus,
                textureManager: TextureManager = engineContext.textureManager,
                singleThreadContext: AddResourceContext = engineContext.singleThreadContext): this(config, eventBus, textureManager, singleThreadContext)

    val skyboxMaterial: SimpleMaterial

    var MATERIALS: MutableMap<String, SimpleMaterial> = LinkedHashMap()

    val defaultMaterial: SimpleMaterial

    val engineDir = config.directories.engineDir

    val materials: List<SimpleMaterial>
        get() = ArrayList(MATERIALS.values)

    var materialsAsStructs = StructArray(1000) { MaterialStruct() }

    init {
        defaultMaterial = getMaterial(SimpleMaterialInfo(name = "default", diffuse = Vector3f(1f, 0f, 0f)).apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/default/default.dds", true, engineDir))
        })
        skyboxMaterial = getMaterial(SimpleMaterialInfo("skybox", materialType = SimpleMaterial.MaterialType.UNLIT))

        eventBus.register(this)
    }

    fun initDefaultMaterials() {

        getMaterial(SimpleMaterialInfo("stone").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/stone_height.png", directory = engineDir))
        })

        getMaterial(SimpleMaterialInfo("stone2").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
        })

        getMaterial(SimpleMaterialInfo("brick").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/brick_height.png", directory = engineDir))
        })

        getMaterial(SimpleMaterialInfo("wood").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/wood_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/wood_normal.png", directory = engineDir))
        })

        getMaterial(SimpleMaterialInfo("stoneWet").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.REFLECTION, textureManager.getTexture("assets/textures/stone_reflection.png", directory = engineDir))
        })
        getMaterial(SimpleMaterialInfo(name = "mirror", diffuse = Vector3f(1f, 1f, 1f), metallic = 1f))

        getMaterial(SimpleMaterialInfo("stoneWet").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/bricks_parallax.dds", true, engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/bricks_parallax_height.dds", directory = engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/bricks_parallax_normal.dds", directory = engineDir))
        })
    }

    fun getMaterial(materialInfo: MaterialInfo): SimpleMaterial {
        if ("" == materialInfo.name) {
            throw IllegalArgumentException("Don't pass a material with null or empty name")
        }
        fun readOrCreateMaterial(): SimpleMaterial {
            val newMaterial = SimpleMaterial(materialInfo)
            newMaterial.materialIndex = MATERIALS.size

            return newMaterial
        }
        addMaterial(materialInfo.name, readOrCreateMaterial())
        return MATERIALS[materialInfo.name]!!
    }

    fun getMaterial(hashMap: HashMap<MAP, String>): SimpleMaterial {
        return getMaterial("Material_" + MATERIALS.size, hashMap)
    }

    fun getMaterial(name: String, hashMap: HashMap<MAP, String>): SimpleMaterial {
        val textures = mutableMapOf<MAP, Texture>()

        hashMap.forEach { map, value ->
            textures[map] = textureManager.getTexture(value, map == MAP.DIFFUSE, engineDir)
        }
        val info = SimpleMaterialInfo(name = name, maps = textures)
        return getMaterial(info)
    }

    fun getMaterial(materialName: String): SimpleMaterial {
        return MATERIALS[materialName] ?: return defaultMaterial
    }

    private fun addMaterial(key: String, material: SimpleMaterial) = singleThreadContext.locked {
        MATERIALS[key] = material
        eventBus.post(MaterialAddedEvent())
    }

    fun putAll(materialLib: Map<String, MaterialInfo>) {
        for (key in materialLib.keys) {
            getMaterial(materialLib[key]!!)
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(MaterialManager::class.java.name)
        val TEXTUREASSETSPATH = "assets/textures/"
        var count = 0

        fun getDirectory(): String {
            return Directories.WORKDIR_NAME + "/assets/materials/"
        }
    }

    override fun extract(renderState: RenderState) {
//        TODO: Remove most of this
        renderState.entitiesState.materialBuffer.ensureCapacityInBytes(SimpleMaterial.bytesPerObject * materials.size)
        renderState.entitiesState.materialBuffer.buffer.rewind()
        materialsAsStructs.resize(materials.size)
        for ((index, material) in materials.withIndex()) {
            val target = materialsAsStructs[index]
            target.diffuse.set(material.materialInfo.diffuse)
            target.metallic = material.materialInfo.metallic
            target.roughness = material.materialInfo.roughness
            target.ambient = material.materialInfo.ambient
            target.parallaxBias = material.materialInfo.parallaxBias
            target.parallaxScale = material.materialInfo.parallaxScale
            target.transparency = material.materialInfo.transparency
            target.materialType = material.materialInfo.materialType
            target.environmentMapId = material.materialInfo.maps[MAP.ENVIRONMENT]?.id ?: 0
            target.diffuseMapHandle = material.materialInfo.maps[MAP.DIFFUSE]?.handle ?: 0
            target.normalMapHandle = material.materialInfo.maps[MAP.NORMAL]?.handle ?: 0
            target.specularMapHandle = material.materialInfo.maps[MAP.SPECULAR]?.handle ?: 0
            target.heightMapHandle = material.materialInfo.maps[MAP.HEIGHT]?.handle ?: 0
            target.occlusionMapHandle = material.materialInfo.maps[MAP.OCCLUSION]?.handle ?: 0
            target.roughnessMapHandle = material.materialInfo.maps[MAP.ROUGHNESS]?.handle ?: 0
        }
        renderState.entitiesState.materialBuffer.resize(materialsAsStructs.size)
        materialsAsStructs.copyTo(renderState.entitiesState.materialBuffer, true)
        renderState.skyBoxMaterialIndex = skyboxMaterial.materialIndex
    }

}
