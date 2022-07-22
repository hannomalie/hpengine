package de.hanno.hpengine.graphics.light.area

import de.hanno.hpengine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.math.Matrix4fStrukt
import de.hanno.hpengine.math.Vector3fStrukt
import struktgen.api.Strukt
import java.nio.ByteBuffer

interface AreaLightStrukt : Strukt {
    context(ByteBuffer) val trafo: Matrix4fStrukt
    context(ByteBuffer) val color: Vector3fStrukt
    context(ByteBuffer) val dummy0: IntStrukt
    context(ByteBuffer) val widthHeightRange: Vector3fStrukt
    context(ByteBuffer) val dummy1: IntStrukt
    companion object
}