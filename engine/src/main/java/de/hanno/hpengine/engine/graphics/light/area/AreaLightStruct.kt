package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.math.Vector3fStrukt
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface AreaLightStrukt : Strukt {
    val ByteBuffer.trafo: Matrix4fStrukt
    val ByteBuffer.color: Vector3fStrukt
    val ByteBuffer.dummy0: IntStrukt
    val ByteBuffer.widthHeightRange: Vector3fStrukt
    val ByteBuffer.dummy1: IntStrukt
    companion object
}