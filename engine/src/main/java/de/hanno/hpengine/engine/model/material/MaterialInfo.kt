package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType.DEFAULT
import de.hanno.hpengine.engine.model.texture.Texture
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.immutableHashMapOf
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.io.Serializable
import java.lang.ref.WeakReference
import java.nio.IntBuffer

interface MaterialInfo {
    val name: String
    val environmentMapType: SimpleMaterial.ENVIRONMENTMAP_TYPE
    val diffuse: Vector3f
    val roughness: Float
    val metallic: Float
    val ambient: Float
    val transparency: Float
    val parallaxScale: Float
    val parallaxBias: Float
    val materialType: SimpleMaterial.MaterialType
    val textureLess: Boolean
    val maps: Map<SimpleMaterial.MAP, Texture>

    fun getHasSpecularMap(): Boolean
    fun getHasNormalMap(): Boolean
    fun getHasDiffuseMap(): Boolean
    fun getHasHeightMap(): Boolean
    fun getHasOcclusionMap(): Boolean
    fun getHasRoughnessMap(): Boolean
}

// TODO: Make this truly immutable
data class SimpleMaterialInfo @JvmOverloads constructor(override val name: String,
                                                        override val diffuse: Vector3f = Vector3f(1f, 1f, 1f),
                                                        override val roughness: Float = 0.95f,
                                                        override val metallic: Float = 0f,
                                                        override val ambient: Float = 0f,
                                                        override val transparency: Float = 0f,
                                                        override val parallaxScale: Float = 0.04f,
                                                        override val parallaxBias: Float = 0.02f,
                                                        override val materialType: SimpleMaterial.MaterialType = DEFAULT,
                                                        private val mapsInternal: ImmutableMap<SimpleMaterial.MAP, Texture> = immutableHashMapOf(),
                                                        override val environmentMapType: SimpleMaterial.ENVIRONMENTMAP_TYPE = SimpleMaterial.ENVIRONMENTMAP_TYPE.GENERATED) : MaterialInfo, Serializable {

    override fun getHasSpecularMap() = mapsInternal.containsKey(SimpleMaterial.MAP.SPECULAR)
    override fun getHasNormalMap() = mapsInternal.containsKey(SimpleMaterial.MAP.NORMAL)
    override fun getHasDiffuseMap() = mapsInternal.containsKey(SimpleMaterial.MAP.DIFFUSE)
    override fun getHasHeightMap() = mapsInternal.containsKey(SimpleMaterial.MAP.HEIGHT)
    override fun getHasOcclusionMap() = mapsInternal.containsKey(SimpleMaterial.MAP.OCCLUSION)
    override fun getHasRoughnessMap() = mapsInternal.containsKey(SimpleMaterial.MAP.ROUGHNESS)

    override val textureLess = mapsInternal.isEmpty()

    private var textureIdsCache: WeakReference<IntBuffer>? = null

    val textureIds: IntBuffer?
        get() {
            cacheTextures()
            return textureIdsCache!!.get()
        }

    fun put(map: SimpleMaterial.MAP, texture: Texture): SimpleMaterialInfo {
        val result = this.copy(mapsInternal = mapsInternal.put(map, texture))
        if (textureIdsCache != null) textureIdsCache!!.clear()

        return result
    }
    fun remove(map: SimpleMaterial.MAP): SimpleMaterialInfo {
        val result = this.copy(mapsInternal = mapsInternal.remove(map))
        if (textureIdsCache != null) textureIdsCache!!.clear()

        return result
    }

    override val maps = mapsInternal as Map<SimpleMaterial.MAP, Texture>

    private fun cacheTextures() {
        if (textureIdsCache == null || textureIdsCache!!.get() == null) {
            textureIdsCache = WeakReference(BufferUtils.createIntBuffer(SimpleMaterial.MAP.values().size))
            textureIdsCache!!.get()!!.rewind()
            val ints = IntArray(SimpleMaterial.MAP.values().size)
            for (i in 0 until SimpleMaterial.MAP.values().size - 1) {
                val texture = mapsInternal[SimpleMaterial.MAP.values()[i]]
                ints[i] = texture?.textureID ?: 0
            }
            textureIdsCache!!.get()!!.put(ints)
            textureIdsCache!!.get()!!.rewind()
        }
    }

    companion object {
        private const val serialVersionUID = 3564429930446909410L
    }
}
