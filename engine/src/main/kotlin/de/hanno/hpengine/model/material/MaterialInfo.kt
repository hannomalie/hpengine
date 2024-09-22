package de.hanno.hpengine.model.material

import de.hanno.hpengine.math.Vector2fStrukt
import de.hanno.hpengine.math.Vector3fStrukt
import de.hanno.hpengine.model.material.Material.MaterialType
import de.hanno.hpengine.model.material.Material.TransparencyType
import struktgen.api.Strukt
import java.nio.ByteBuffer

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

    context(ByteBuffer) var diffuseMipmapBias: Float
    context(ByteBuffer) var diffuseMapIndex: Int
    context(ByteBuffer) var dummy1: Float
    context(ByteBuffer) var dummy2: Float

    companion object
}
