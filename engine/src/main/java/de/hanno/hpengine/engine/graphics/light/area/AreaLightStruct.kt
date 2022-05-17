package de.hanno.hpengine.engine.graphics.light.area

import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStrukt
import de.hanno.hpengine.engine.math.Matrix4fStrukt
import de.hanno.hpengine.engine.math.Vector3fStrukt
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