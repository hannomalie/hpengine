package de.hanno.hpengine.engine.model.material

import MaterialStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.struct.copyTo
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import struktgen.TypedBuffer
import java.nio.ByteBuffer
import de.hanno.hpengine.engine.component.artemis.ModelComponent as NewModelComponent

@All(NewModelComponent::class)
class MaterialManager(
    val config: Config,
    val textureManager: TextureManager,
    val singleThreadContext: AddResourceContext
) : BaseEntitySystem() {

    lateinit var modelComponentComponentMapper: ComponentMapper<NewModelComponent>

    val materials: MutableList<Material> = mutableListOf()

    val engineDir = config.directories.engineDir

    var materialsBuffer = TypedBuffer(BufferUtils.createByteBuffer(1000 * MaterialStrukt.type.sizeInBytes), MaterialStrukt.type)

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

    override fun inserted(entityId: Int) {
        val modelComponent = modelComponentComponentMapper[entityId]
        modelComponent.modelComponentDescription.material?.let {
            registerMaterial(it)
        }
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

    fun extract(renderState: RenderState) {
//        TODO: Remove most of this
        renderState.entitiesState.materialBuffer.ensureCapacityInBytes(SimpleMaterial.bytesPerObject * materials.size)
        renderState.entitiesState.materialBuffer.buffer.rewind()
        materialsBuffer = materialsBuffer.resize(materials.size)

        materialsBuffer.forEachIndexed(untilIndex = materials.size) { index, it ->
            val material = materials[index]

            it.run {
                diffuse.run {
                    x = material.materialInfo.diffuse.x
                    y = material.materialInfo.diffuse.y
                    z = material.materialInfo.diffuse.z
                }
                metallic = material.materialInfo.metallic
                roughness = material.materialInfo.roughness
                ambient = material.materialInfo.ambient
                parallaxBias = material.materialInfo.parallaxBias
                parallaxScale = material.materialInfo.parallaxScale
                transparency = material.materialInfo.transparency
                materialType = material.materialInfo.materialType
                lodFactor = material.materialInfo.lodFactor
                useWorldSpaceXZAsTexCoords = if(material.materialInfo.useWorldSpaceXZAsTexCoords) 1 else 0
                environmentMapId = material.materialInfo.maps[MAP.ENVIRONMENT]?.id ?: 0
                diffuseMapHandle = material.materialInfo.maps[MAP.DIFFUSE]?.handle ?: 0
                normalMapHandle = material.materialInfo.maps[MAP.NORMAL]?.handle ?: 0
                specularMapHandle = material.materialInfo.maps[MAP.SPECULAR]?.handle ?: 0
                heightMapHandle = material.materialInfo.maps[MAP.HEIGHT]?.handle ?: 0
                displacementMapHandle = material.materialInfo.maps[MAP.DISPLACEMENT]?.handle ?: 0
                roughnessMapHandle = material.materialInfo.maps[MAP.ROUGHNESS]?.handle ?: 0
                uvScale.run {
                    x = material.materialInfo.uvScale.x
                    y = material.materialInfo.uvScale.y
                }
            }
        }
        renderState.entitiesState.materialBuffer.resize(materialsBuffer.size)
        materialsBuffer.byteBuffer.copyTo(renderState.entitiesState.materialBuffer.buffer, true)
    }


    companion object {
        fun createDefaultMaterial(config: Config, textureManager: TextureManager): SimpleMaterial {
            return SimpleMaterial("default", createDefaultMaterialInfo(config, textureManager))
        }
        fun createDefaultMaterialInfo(config: Config, textureManager: TextureManager) = MaterialInfo(diffuse = Vector3f(1f, 0f, 0f)).apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/default/default.dds", true, config.engineDir))
        }
    }

    override fun processSystem() { }
}

val TypedBuffer<MaterialStrukt>.size: Int
    get() = byteBuffer.capacity() / struktType.sizeInBytes

private fun TypedBuffer<MaterialStrukt>.resize(size: Int): TypedBuffer<MaterialStrukt> = this.let {
    val resized = it.byteBuffer.resize(size)
    if (resized != it) TypedBuffer(resized, MaterialStrukt.type) else it
}

fun ByteBuffer.resize(sizeInBytes: Int, copyContent: Boolean = true) = if(capacity() < sizeInBytes) {
    BufferUtils.createByteBuffer(sizeInBytes).apply {
        if(copyContent) {
            put(this@resize)
        }
    }
} else this