package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.Uniforms
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
    var environmentMapId by 0

    var diffuseMapHandle: Long by 0L
    var normalMapHandle: Long by 0L
    var specularMapHandle: Long by 0L
    var heightMapHandle: Long by 0L

    var occlusionMapHandle: Long by 0L
    var roughnessMapHandle: Long by 0L

}

interface MaterialInfo {
    var isShadowCasting: Boolean
    var name: String
    val environmentMapType: ENVIRONMENTMAP_TYPE
    val diffuse: Vector3f
    val roughness: Float
    val metallic: Float
    val ambient: Float
    val transparency: Float
    val parallaxScale: Float
    val parallaxBias: Float
    var materialType: MaterialType
    var cullBackFaces: Boolean
    val transparencyType: TransparencyType
    val textureLess: Boolean
    val maps: Map<MAP, Texture>
    var program: Program<FirstPassUniforms>?

    fun getHasSpecularMap(): Boolean
    fun getHasNormalMap(): Boolean
    fun getHasDiffuseMap(): Boolean
    fun getHasHeightMap(): Boolean
    fun getHasOcclusionMap(): Boolean
    fun getHasRoughnessMap(): Boolean
    fun put(map: MAP, texture: Texture)
    fun remove(map: MAP)
}

data class SimpleMaterialInfo @JvmOverloads constructor(override var name: String,
                                                        override var diffuse: Vector3f = Vector3f(1f, 1f, 1f),
                                                        override var roughness: Float = 0.95f,
                                                        override var metallic: Float = 0f,
                                                        override var ambient: Float = 0f,
                                                        override var transparency: Float = 0f,
                                                        override var parallaxScale: Float = 0.04f,
                                                        override var parallaxBias: Float = 0.02f,
                                                        override var materialType: MaterialType = DEFAULT,
                                                        override var transparencyType: TransparencyType = TransparencyType.BINARY,
                                                        override var cullBackFaces: Boolean = materialType == MaterialType.FOLIAGE,
                                                        override val maps: MutableMap<MAP, Texture> = mutableMapOf(),
                                                        override val environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED,
                                                        override var isShadowCasting: Boolean = true,
                                                        override var program: Program<FirstPassUniforms>? = null) : MaterialInfo, Serializable {

    override fun getHasSpecularMap() = maps.containsKey(MAP.SPECULAR)
    override fun getHasNormalMap() = maps.containsKey(MAP.NORMAL)
    override fun getHasDiffuseMap() = maps.containsKey(MAP.DIFFUSE)
    override fun getHasHeightMap() = maps.containsKey(MAP.HEIGHT)
    override fun getHasOcclusionMap() = maps.containsKey(MAP.OCCLUSION)
    override fun getHasRoughnessMap() = maps.containsKey(MAP.ROUGHNESS)

    override val textureLess = maps.isEmpty()

    override fun put(map: MAP, texture: Texture) {
        maps[map] = texture
    }
    override fun remove(map: MAP) {
        maps.remove(map)
    }

    companion object {
        private const val serialVersionUID = 3564429930446909410L
    }
}
