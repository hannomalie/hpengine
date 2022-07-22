package de.hanno.hpengine.model.material

import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.model.material.Material.*
import de.hanno.hpengine.ressources.CodeSource
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
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float

    companion object
}

interface Vector3fStrukt : Strukt {
    context(ByteBuffer) var x: Float
    context(ByteBuffer) var y: Float
    context(ByteBuffer) var z: Float

    companion object
}

interface MaterialStrukt : Strukt {
    context(ByteBuffer) val diffuse: Vector3fStrukt
    context(ByteBuffer) var metallic: Float

    context(ByteBuffer) var roughness: Float
    context(ByteBuffer) var ambient: Float
    context(ByteBuffer) var parallaxBias: Float
    context(ByteBuffer) var parallaxScale: Float

    context(ByteBuffer) var transparency: Float
    context(ByteBuffer) var materialType: MaterialType
    context(ByteBuffer) var transparencyType: TransparencyType
    context(ByteBuffer) var environmentMapId: Int

    context(ByteBuffer) var diffuseMapHandle: Long
    context(ByteBuffer) var normalMapHandle: Long
    context(ByteBuffer) var specularMapHandle: Long
    context(ByteBuffer) var heightMapHandle: Long

    context(ByteBuffer) var displacementMapHandle: Long
    context(ByteBuffer) var roughnessMapHandle: Long

    context(ByteBuffer) val uvScale: Vector2fStrukt
    context(ByteBuffer) var lodFactor: Float
    context(ByteBuffer) var useWorldSpaceXZAsTexCoords: Int

    companion object
}
