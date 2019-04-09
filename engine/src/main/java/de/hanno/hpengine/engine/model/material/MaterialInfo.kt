package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.model.material.SimpleMaterial.ENVIRONMENTMAP_TYPE
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType.DEFAULT
import de.hanno.hpengine.engine.model.material.SimpleMaterial.TransparencyType
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.model.texture.TextureDimension2D
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.struct.Struct
import org.joml.Vector3f
import java.io.Serializable

class MaterialStruct(val environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED): Struct() {
    val diffuse by HpVector3f()
    var metallic by 0.0f

    var roughness by 0.0f
    var ambient by 0.0f
    var parallaxBias by 0.0f
    var parallaxScale by 0.0f

    var transparency by 0.0f
    var materialType by MaterialType::class.java
    val transparencyType by TransparencyType::class.java
    val dummy1 by 0.0f

    var diffuseMapHandle: Long by 0L
    var normalMapHandle: Long by 0L
    var specularMapHandle: Long by 0L
    var heightMapHandle: Long by 0L
    var occlusionMapHandle: Long by 0L
    var roughnessMapHandle: Long by 0L

}

interface MaterialInfo {
    val name: String
    val environmentMapType: ENVIRONMENTMAP_TYPE
    val diffuse: Vector3f
    val roughness: Float
    val metallic: Float
    val ambient: Float
    val transparency: Float
    val parallaxScale: Float
    val parallaxBias: Float
    val materialType: MaterialType
    val transparencyType: TransparencyType
    val textureLess: Boolean
    val maps: Map<MAP, Texture<TextureDimension2D>>

    fun getHasSpecularMap(): Boolean
    fun getHasNormalMap(): Boolean
    fun getHasDiffuseMap(): Boolean
    fun getHasHeightMap(): Boolean
    fun getHasOcclusionMap(): Boolean
    fun getHasRoughnessMap(): Boolean
    fun put(map: MAP, texture: Texture<TextureDimension2D>): MaterialInfo
    fun remove(map: MAP): MaterialInfo
    fun copyXXX(diffuse: Vector3f = this.diffuse,
                roughness: Float = this.roughness,
                metallic: Float = this.metallic,
                ambient: Float = this.ambient,
                transparency: Float = this.transparency,
                parallaxScale: Float = this.parallaxScale,
                parallaxBias: Float = this.parallaxBias,
                materialType: MaterialType = this.materialType,
                transparencyType: TransparencyType = this.transparencyType,
                textureLess: Boolean = this.textureLess,
                maps: Map<MAP, Texture<TextureDimension2D>> = this.maps,
                environmentMapType: ENVIRONMENTMAP_TYPE = this.environmentMapType): MaterialInfo
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
                                                        override val materialType: MaterialType = DEFAULT,
                                                        override val transparencyType: TransparencyType = TransparencyType.BINARY,
                                                        private val mapsInternal: MutableMap<MAP, Texture<TextureDimension2D>> = hashMapOf(),
                                                        override val environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED) : MaterialInfo, Serializable {

    override fun copyXXX(diffuse: Vector3f, roughness: Float, metallic: Float, ambient: Float, transparency: Float, parallaxScale: Float, parallaxBias: Float, materialType: MaterialType, transparencyType: TransparencyType, textureLess: Boolean, maps: Map<MAP, Texture<TextureDimension2D>>, environmentMapType: ENVIRONMENTMAP_TYPE): MaterialInfo {
        return copy(
                name = name,
                diffuse = diffuse,
                roughness = roughness,
                metallic = metallic,
                ambient = ambient,
                transparency = transparency,
                parallaxScale = parallaxScale,
                parallaxBias = parallaxBias,
                materialType = materialType,
                transparencyType = transparencyType,
                mapsInternal = HashMap(maps),
                environmentMapType = environmentMapType
        )
    }


    override fun getHasSpecularMap() = mapsInternal.containsKey(MAP.SPECULAR)
    override fun getHasNormalMap() = mapsInternal.containsKey(MAP.NORMAL)
    override fun getHasDiffuseMap() = mapsInternal.containsKey(MAP.DIFFUSE)
    override fun getHasHeightMap() = mapsInternal.containsKey(MAP.HEIGHT)
    override fun getHasOcclusionMap() = mapsInternal.containsKey(MAP.OCCLUSION)
    override fun getHasRoughnessMap() = mapsInternal.containsKey(MAP.ROUGHNESS)

    override val textureLess = mapsInternal.isEmpty()

    override fun put(map: MAP, texture: Texture<TextureDimension2D>): SimpleMaterialInfo {
        return copy(mapsInternal = mapsInternal.apply { put(map, texture) })
    }
    override fun remove(map: MAP): SimpleMaterialInfo {
        return copy(mapsInternal = mapsInternal.apply { remove(map)})
    }

    override val maps = mapsInternal as Map<MAP, Texture<TextureDimension2D>>

    companion object {
        private const val serialVersionUID = 3564429930446909410L
    }
}
