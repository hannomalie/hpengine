package de.hanno.hpengine.graphics.light.probe

import de.hanno.hpengine.math.Vector3fStrukt
import de.hanno.hpengine.graphics.texture.CubeMap
import org.joml.Vector3f
import org.lwjgl.opengl.GL13
import struktgen.api.Strukt
import java.nio.ByteBuffer
data class AmbientCubeData(val position: Vector3f, val cubeMap: CubeMap, val distanceMap: CubeMap, val index: Int)
interface AmbientCube: Strukt {
    context(ByteBuffer) val position: Vector3fStrukt
    context(ByteBuffer) val dummy: Float
    context(ByteBuffer) val cubeMapHandle: Double
    context(ByteBuffer) val distanceMapHandle: Double
    companion object
}

sealed class CubemapSide(val value: Int) {
    object PositiveX : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X)
    object NegativeX : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_X)
    object PositiveY : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Y)
    object NegativeY : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y)
    object PositiveZ : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_Z)
    object NegativeZ : CubemapSide(GL13.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z)
}
