package de.hanno.hpengine.graphics.light.probe

import de.hanno.hpengine.math.Vector3fStrukt
import de.hanno.hpengine.graphics.texture.CubeMap
import org.joml.Vector3f
import struktgen.api.Strukt
import java.nio.ByteBuffer

data class AmbientCubeData(val position: Vector3f, val cubeMap: CubeMap, val distanceMap: CubeMap, val index: Int)
interface AmbientCube : Strukt {
    context(ByteBuffer) val position: Vector3fStrukt
    context(ByteBuffer) val dummy: Float
    context(ByteBuffer) val cubeMapHandle: Double
    context(ByteBuffer) val distanceMapHandle: Double

    companion object
}
