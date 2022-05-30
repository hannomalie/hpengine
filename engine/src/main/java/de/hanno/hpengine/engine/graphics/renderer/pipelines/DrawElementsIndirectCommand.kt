package de.hanno.hpengine.engine.graphics.renderer.pipelines

import struktgen.api.Strukt
import java.nio.ByteBuffer

data class DrawElementsIndirectCommand(
    var count: Int = 0,
    var instanceCount: Int = 0,
    var firstIndex: Int = 0,
    var baseVertex: Int = 0,
    var baseInstance: Int = 0,
)

interface DrawElementsIndirectCommandStrukt : Strukt {
    context(ByteBuffer) var count: Int
    context(ByteBuffer) var instanceCount: Int
    context(ByteBuffer) var firstIndex: Int
    context(ByteBuffer) var baseVertex: Int
    context(ByteBuffer) var baseInstance: Int

    companion object
}

