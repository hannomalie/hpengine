package de.hanno.hpengine.model.material

import MaterialStruktImpl.Companion.sizeInBytes
import MaterialStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.annotations.One
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.buffer.typed
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.Texture
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.model.MaterialComponent
import de.hanno.hpengine.model.ModelComponent
import de.hanno.hpengine.model.material.Material.MAP
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.system.Clearable
import de.hanno.hpengine.system.Extractor
import org.joml.Vector3f
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import struktgen.api.TypedBuffer
import struktgen.api.forEachIndexed
import java.nio.ByteBuffer

@One(
    ModelComponent::class,
    MaterialComponent::class,
)
@Single(binds=[BaseSystem::class, MaterialSystem::class])
class MaterialSystem(
    private val config: Config,
    private val textureManager: OpenGLTextureManager,
    private val singleThreadContext: AddResourceContext,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val renderStateContext: RenderStateContext,
    private val graphicsApi: GraphicsApi,
) : BaseEntitySystem(), Clearable, Extractor {

    val materialBuffer = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(MaterialStrukt.type.sizeInBytes).typed(MaterialStrukt.type)
    }

    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

    val materials: MutableList<Material> = mutableListOf()

    val engineDir = config.directories.engineDir

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
        if(materials.firstOrNull { it.name == material.name } == null) {
            materials.add(material)
        }
    }

    override fun extract(currentWriteState: RenderState) {
//        TODO: Remove most of this
        currentWriteState[materialBuffer].ensureCapacityInBytes(MaterialStrukt.sizeInBytes * materials.size)
        currentWriteState[materialBuffer].buffer.rewind()

        currentWriteState[materialBuffer].forEachIndexed(untilIndex = materials.size) { index, targetMaterial ->
            val material = materials[index]

            targetMaterial.run {
                diffuse.x = material.diffuse.x
                diffuse.y = material.diffuse.y
                diffuse.z = material.diffuse.z
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
                diffuseMapHandle = material.deriveHandle(MAP.DIFFUSE, textureManager.defaultTexture)
                diffuseMipmapBias = material.deriveDiffuseMipMapBias()
                normalMapHandle = material.deriveHandle(MAP.NORMAL)
                specularMapHandle = material.deriveHandle(MAP.SPECULAR)
                heightMapHandle = material.deriveHandle(MAP.HEIGHT)
                displacementMapHandle = material.deriveHandle(MAP.DISPLACEMENT)
                roughnessMapHandle = material.deriveHandle(MAP.ROUGHNESS)
                uvScale.x = material.uvScale.x
                uvScale.y = material.uvScale.y
            }
        }
    }
    private fun Material.deriveDiffuseMipMapBias(): Int = if(maps.containsKey(MAP.DIFFUSE)) {
        when(val uploadState = maps[MAP.DIFFUSE]!!.uploadState) {
            is UploadState.Uploading -> uploadState.maxMipMapLoaded
            else -> 0
        }
    } else 0

    private fun Material.deriveHandle(key: MAP, fallbackTexture: Texture? = null): Long = maps[key]?.let {
        val fallbackHandle = (fallbackTexture?.handle ?: 0)
        when(it.uploadState) {
            UploadState.NotUploaded -> fallbackHandle
            UploadState.Uploaded -> it.handle
            is UploadState.Uploading -> it.handle
        }
    } ?: 0


    companion object {
        fun createDefaultMaterial(config: Config, textureManager: OpenGLTextureManager) = Material("default", diffuse = Vector3f(1f, 0f, 0f)).apply {
            put(MAP.DIFFUSE, textureManager.getTexture("assets/textures/default/default.dds", true, config.engineDir))
        }
    }

    override fun processSystem() { }
    override fun clear() {
        materials.clear()
    }

    fun indexOf(material: Material): Int = materials.indexOf(material)
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