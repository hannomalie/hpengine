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
    context(ByteBuffer) val count: IntStrukt
    context(ByteBuffer) val primCount: IntStrukt
    context(ByteBuffer) val firstIndex: IntStrukt
    context(ByteBuffer) val baseVertex: IntStrukt
    context(ByteBuffer) val baseInstance: IntStrukt

    companion object
}

