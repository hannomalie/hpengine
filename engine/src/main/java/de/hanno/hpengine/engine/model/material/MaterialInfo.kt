package de.hanno.hpengine.engine.model.material

import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.model.material.Material.*
import de.hanno.hpengine.engine.model.material.Material.MaterialType.DEFAULT
import de.hanno.hpengine.engine.model.material.Material.TransparencyType.BINARY
import de.hanno.hpengine.engine.model.texture.Texture
import de.hanno.hpengine.util.ressources.CodeSource
import org.joml.Vector2f
import org.joml.Vector3f
import struktgen.api.Strukt
import java.nio.ByteBuffer

data class ProgramDescription(
    val fragmentShaderSource: CodeSource,
    val vertexShaderSource: CodeSource,
    val tesselationControlShaderSource : CodeSource? = null,
    val tesselationEvaluationShaderSource : CodeSource? = null,
    val geometryShaderSource: CodeSource? = null,
    val defines: Defines? = null,
)

data class MaterialInfo @JvmOverloads constructor(
    val diffuse: Vector3f = Vector3f(1f, 1f, 1f),
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
    var transparencyType: TransparencyType = BINARY,
    var cullBackFaces: Boolean = materialType == MaterialType.FOLIAGE,
    var depthTest: Boolean = true,
    val maps: MutableMap<MAP, Texture> = mutableMapOf(),
    var environmentMapType: ENVIRONMENTMAP_TYPE = ENVIRONMENTMAP_TYPE.GENERATED,
    var isShadowCasting: Boolean = true,
    var programDescription: ProgramDescription? = null,
) {

    fun put(map: MAP, texture: Texture) {
        maps[map] = texture
    }

    fun remove(map: MAP) {
        maps.remove(map)
    }
}


interface Vector2fStrukt : Strukt {
    var ByteBuffer.x: Float
    var ByteBuffer.y: Float

    companion object
}

interface Vector3fStrukt : Strukt {
    var ByteBuffer.x: Float
    var ByteBuffer.y: Float
    var ByteBuffer.z: Float

    companion object
}

interface MaterialStrukt : Strukt {
    val ByteBuffer.diffuse: Vector3fStrukt
    var ByteBuffer.metallic: Float

    var ByteBuffer.roughness: Float
    var ByteBuffer.ambient: Float
    var ByteBuffer.parallaxBias: Float
    var ByteBuffer.parallaxScale: Float

    var ByteBuffer.transparency: Float
    var ByteBuffer.materialType: MaterialType
    var ByteBuffer.transparencyType: TransparencyType
    var ByteBuffer.environmentMapId: Int

    var ByteBuffer.diffuseMapHandle: Long
    var ByteBuffer.normalMapHandle: Long
    var ByteBuffer.specularMapHandle: Long
    var ByteBuffer.heightMapHandle: Long

    var ByteBuffer.displacementMapHandle: Long
    var ByteBuffer.roughnessMapHandle: Long

    val ByteBuffer.uvScale: Vector2fStrukt
    var ByteBuffer.lodFactor: Float
    var ByteBuffer.useWorldSpaceXZAsTexCoords: Int

    companion object
}
