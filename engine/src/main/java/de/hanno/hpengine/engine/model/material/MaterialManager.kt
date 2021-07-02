package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.textureManager
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.ModelComponentSystem
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.struct.StructArray
import de.hanno.struct.copyTo
import de.hanno.struct.resize
import org.joml.Vector3f
import org.koin.core.component.get

class MaterialManager(
    val config: Config,
    val textureManager: TextureManager,
    val singleThreadContext: AddResourceContext
) : Manager {

    constructor(
        engineContext: EngineContext,
        config: Config = engineContext.config,
        textureManager: TextureManager = engineContext.textureManager,
        singleThreadContext: AddResourceContext = engineContext.addResourceContext
    ): this(
        config,
        textureManager,
        singleThreadContext
    )

    val materials: MutableList<Material> = mutableListOf()

    val engineDir = config.directories.engineDir

    var materialsAsStructs = StructArray(1000) { MaterialStruct() }

    fun initDefaultMaterials() {

        registerMaterial("stone", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/stone_height.png", directory = engineDir))
        })

        registerMaterial("stone2", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
        })

        registerMaterial("brick", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/brick_height.png", directory = engineDir))
        })

        registerMaterial("wood", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/wood_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/wood_normal.png", directory = engineDir))
        })

        registerMaterial("stoneWet", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.REFLECTION, textureManager.getTexture("assets/textures/stone_reflection.png", directory = engineDir))
        })
        registerMaterial("mirror", MaterialInfo(diffuse = Vector3f(1f, 1f, 1f), metallic = 1f))

        registerMaterial("stoneWet", MaterialInfo().apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/bricks_parallax.dds", true, engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/bricks_parallax_height.dds", directory = engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/bricks_parallax_normal.dds", directory = engineDir))
        })
    }

    fun getMaterial(name: String): Material? = materials.firstOrNull { it.name == name }

    fun registerMaterial(name: String, materialInfo: MaterialInfo): SimpleMaterial = SimpleMaterial(name, materialInfo).apply {
        registerMaterial(this)
    }

    override fun beforeSetScene(nextScene: Scene) {
        clear()
        registerMaterials(nextScene.get<ModelComponentSystem>().components.flatMap { it.materials })
    }

    override fun onEntityAdded(entities: List<Entity>) {
        registerMaterials(entities.mapNotNull { it.getComponent(ModelComponent::class.java) }.flatMap { it.materials }.distinct())
    }
    fun registerMaterial(material: Material) = singleThreadContext.launch {
//        This happens often, I have to reconsider that somehow
//        require(materials.none { material.name == it.name }) { "Material with name ${material.name} already registered!" }

        if(!this.materials.contains(material)) {
            material.materialIndex = materials.size
            materials.add(material)
        }
    }
    fun registerMaterials(materials: List<Material>) = singleThreadContext.launch {
        materials.distinct().forEach { material ->
            if(!this.materials.contains(material)) {
                material.materialIndex = this.materials.size
                this.materials.add(material)
            }
        }
    }

    override fun extract(scene: Scene, renderState: RenderState) {
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
            target.lodFactor = material.materialInfo.lodFactor
            target.useWorldSpaceXZAsTexCoords = if(material.materialInfo.useWorldSpaceXZAsTexCoords) 1 else 0
            target.environmentMapId = material.materialInfo.maps[MAP.ENVIRONMENT]?.id ?: 0
            target.diffuseMapHandle = material.materialInfo.maps[MAP.DIFFUSE]?.handle ?: 0
            target.normalMapHandle = material.materialInfo.maps[MAP.NORMAL]?.handle ?: 0
            target.specularMapHandle = material.materialInfo.maps[MAP.SPECULAR]?.handle ?: 0
            target.heightMapHandle = material.materialInfo.maps[MAP.HEIGHT]?.handle ?: 0
            target.displacementMapHandle = material.materialInfo.maps[MAP.DISPLACEMENT]?.handle ?: 0
            target.roughnessMapHandle = material.materialInfo.maps[MAP.ROUGHNESS]?.handle ?: 0
            target.uvScale.set(material.materialInfo.uvScale)
        }
        renderState.entitiesState.materialBuffer.resize(materialsAsStructs.size)
        materialsAsStructs.copyTo(renderState.entitiesState.materialBuffer, true)
    }

    companion object {
        fun createDefaultMaterial(config: Config, textureManager: TextureManager): SimpleMaterial {
            return SimpleMaterial("default", createDefaultMaterialInfo(config, textureManager))
        }
        fun createDefaultMaterialInfo(config: Config, textureManager: TextureManager) = MaterialInfo(diffuse = Vector3f(1f, 0f, 0f)).apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/default/default.dds", true, config.engineDir))
        }
    }
}
