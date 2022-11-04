package de.hanno.hpengine.model.material

import MaterialStruktImpl.Companion.sizeInBytes
import MaterialStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import de.hanno.hpengine.artemis.MaterialComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.model.material.Material.MAP
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.system.Extractor
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import java.nio.ByteBuffer
import de.hanno.hpengine.artemis.ModelComponent

@One(
    ModelComponent::class,
    MaterialComponent::class,
)
class MaterialManager(
    val config: Config,
    val textureManager: OpenGLTextureManager,
    val singleThreadContext: AddResourceContext
) : BaseEntitySystem(), Clearable, Extractor {

    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

    val materials: MutableList<Material> = mutableListOf()

    val engineDir = config.directories.engineDir

    var materialsBuffer = TypedBuffer(BufferUtils.createByteBuffer(1000 * MaterialStrukt.type.sizeInBytes), MaterialStrukt.type)

    fun initDefaultMaterials() {

        registerMaterial(Material("stone").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/stone_height.png", directory = engineDir))
        })

        registerMaterial(Material("stone2").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
        })

        registerMaterial(Material("brick").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/brick.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/brick_normal.png", directory = engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/brick_height.png", directory = engineDir))
        })

        registerMaterial(Material("wood").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/wood_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/wood_normal.png", directory = engineDir))
        })

        registerMaterial(Material("stoneWet").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/stone_diffuse.png", true, engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/stone_normal.png", directory = engineDir))
            put(MAP.REFLECTION, textureManager.getTexture("assets/textures/stone_reflection.png", directory = engineDir))
        })
        registerMaterial(Material("mirror", diffuse = Vector3f(1f, 1f, 1f), metallic = 1f))

        registerMaterial(Material("stoneWet").apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/bricks_parallax.dds", true, engineDir))
            put(MAP.HEIGHT, textureManager.getTexture("assets/textures/bricks_parallax_height.dds", directory = engineDir))
            put(MAP.NORMAL, textureManager.getTexture("assets/textures/bricks_parallax_normal.dds", directory = engineDir))
        })
    }

    fun getMaterial(name: String): Material? = materials.firstOrNull { it.name == name }

    override fun inserted(entityId: Int) {
        val materialComponentOrNull = materialComponentMapper[entityId]
        materialComponentOrNull?.let {
            registerMaterial(it.material)
        }
    }
    fun registerMaterial(material: Material) = singleThreadContext.launch {
//        This happens often, I have to reconsider that somehow
//        require(materials.none { material.name == it.name }) { "Material with name ${material.name} already registered!" }

        material.materialIndex = materials.size
        materials.add(material)
    }

    override fun extract(currentWriteState: RenderState) {
//        TODO: Remove most of this
        currentWriteState.entitiesState.materialBuffer.ensureCapacityInBytes(MaterialStrukt.sizeInBytes * materials.size)
        currentWriteState.entitiesState.materialBuffer.buffer.rewind()
        materialsBuffer = materialsBuffer.resize(sizeInBytes = materials.size * MaterialStrukt.sizeInBytes)
        currentWriteState.entitiesState.materialBuffer.resize(materialsBuffer.size)

        currentWriteState.entitiesState.materialBuffer.typedBuffer.forEachIndexed(untilIndex = materials.size) { index, it ->
            val material = materials[index]

            it.run {
                diffuse.run {
                    x = material.diffuse.x
                    y = material.diffuse.y
                    z = material.diffuse.z
                }
                metallic = material.metallic
                roughness = material.roughness
                ambient = material.ambient
                parallaxBias = material.parallaxBias
                parallaxScale = material.parallaxScale
                transparency = material.transparency
                materialType = material.materialType
                lodFactor = material.lodFactor
                useWorldSpaceXZAsTexCoords = if (material.useWorldSpaceXZAsTexCoords) 1 else 0
                environmentMapId = material.maps[MAP.ENVIRONMENT]?.id ?: 0
                diffuseMapHandle = material.maps[MAP.DIFFUSE]?.handle ?: 0
                normalMapHandle = material.maps[MAP.NORMAL]?.handle ?: 0
                specularMapHandle = material.maps[MAP.SPECULAR]?.handle ?: 0
                heightMapHandle = material.maps[MAP.HEIGHT]?.handle ?: 0
                displacementMapHandle = material.maps[MAP.DISPLACEMENT]?.handle ?: 0
                roughnessMapHandle = material.maps[MAP.ROUGHNESS]?.handle ?: 0
                uvScale.run {
                    x = material.uvScale.x
                    y = material.uvScale.y
                }
            }
        }
//        materialsBuffer.byteBuffer.copyTo(currentWriteState.entitiesState.materialBuffer.buffer, true)
    }


    companion object {
        fun createDefaultMaterial(config: Config, textureManager: OpenGLTextureManager) = Material("default", diffuse = Vector3f(1f, 0f, 0f)).apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/default/default.dds", true, config.engineDir))
        }
    }

    override fun processSystem() { }
    override fun clear() {
        materials.clear()
    }
}

private fun TypedBuffer<MaterialStrukt>.resize(sizeInBytes: Int): TypedBuffer<MaterialStrukt> = this.let {
    val resized = it.byteBuffer.resize(sizeInBytes)
    if (resized != it) TypedBuffer(resized, MaterialStrukt.type) else it
}

fun ByteBuffer.resize(sizeInBytes: Int, copyContent: Boolean = true) = if(capacity() < sizeInBytes) {
    BufferUtils.createByteBuffer(sizeInBytes).apply {
        if(copyContent) {
            put(this@resize)
        }
    }
} else this