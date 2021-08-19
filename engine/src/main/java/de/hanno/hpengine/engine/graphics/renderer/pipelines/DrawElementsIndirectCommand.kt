package de.hanno.hpengine.engine.graphics.renderer.pipelines

import struktgen.api.Strukt
import java.nio.ByteBuffer

data class DrawElementsIndirectCommand(
    var count: Int = 0,
    var primCount: Int = 0,
    var firstIndex: Int = 0,
    var baseVertex: Int = 0,
    var baseInstance: Int = 0,
)

interface DrawElementsIndirectCommandStrukt : Strukt {
    val ByteBuffer.count: IntStrukt
    val ByteBuffer.primCount: IntStrukt
    val ByteBuffer.firstIndex: IntStrukt
    val ByteBuffer.baseVertex: IntStrukt
    val ByteBuffer.baseInstance: IntStrukt

    companion object
}

