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
