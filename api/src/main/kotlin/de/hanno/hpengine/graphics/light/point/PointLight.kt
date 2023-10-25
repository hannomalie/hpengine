package de.hanno.hpengine.graphics.light.point

import de.hanno.hpengine.math.Vector3fStrukt
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface PointLightStruct : Strukt {
    context(ByteBuffer) val position: Vector3fStrukt
    context(ByteBuffer) var radius: Float
    context(ByteBuffer) val color: Vector3fStrukt
    context(ByteBuffer) val dummy: Float

    companion object
}