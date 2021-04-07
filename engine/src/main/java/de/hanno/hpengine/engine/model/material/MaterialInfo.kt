package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.model.material.SimpleMaterial.ENVIRONMENTMAP_TYPE
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MAP
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType
import de.hanno.hpengine.engine.model.material.SimpleMaterial.MaterialType.DEFAULT
import de.hanno.hpengine.engine.model.material.SimpleMaterial.TransparencyType
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.engine.scene.HpVector2f
import de.hanno.hpengine.engine.scene.HpVector3f
import de.hanno.struct.Struct
import org.joml.Vector2f
import org.joml.Vector3f

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

    var displacementMapHandle: Long by 0L
    var roughnessMapHandle: Long by 0L

    var uvScale by HpVector2f()
    var lodFactor by 0.0f
    var useWorldSpaceXZAsTexCoords by 0

}

data class MaterialInfo @JvmOverloads constructor(val diffuse: Vector3f = Vector3f(1f, 1f, 1f),
                                                  var roughness: Float = 0.95f,
                                                  var metallic: Float = 0f,
                                                  var ambient: Float = 0f,
                                                  var transparency: Float = 0f,
                                                  var parallaxScale: Float = 0.04f,
                                                  var parallaxBias: Float = 0.02f,
                                                  var uvScale: Vector2f = Vector2f(1.0f, 1.0f),
                                                  var lodFactor: Float = 100f,
                                                  var useWorldSpaceXZAsTexCoords: Boolean = false,
                                                  var materialType: MaterialType = DEFAULT,
                                                  var transparencyType: TransparencyType = TransparencyType.BINARY,
                                                  var cullBackFaces: Boolean = materialType == MaterialType.FOLIAGE,
                                                  var depthTest: Boolean = true,
                                                  val maps: MutableMap<MAP, Texture> = mutableMapOf(),
                                                  var environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED,
                                                  var isShadowCasting: Boolean = true,
                                                  var program: Program<FirstPassUniforms>? = null) {

    // TODO rename, remove "get"
    fun getHasSpecularMap() = maps.containsKey(MAP.SPECULAR)
    fun getHasNormalMap() = maps.containsKey(MAP.NORMAL)
    fun getHasDiffuseMap() = maps.containsKey(MAP.DIFFUSE)
    fun getHasHeightMap() = maps.containsKey(MAP.HEIGHT)
    fun getHasDisplacentMap() = maps.containsKey(MAP.DISPLACEMENT)
    fun getHasRoughnessMap() = maps.containsKey(MAP.ROUGHNESS)

    val textureLess = maps.isEmpty()

    fun put(map: MAP, texture: Texture) {
        maps[map] = texture
    }
    fun remove(map: MAP) {
        maps.remove(map)
    }
}
