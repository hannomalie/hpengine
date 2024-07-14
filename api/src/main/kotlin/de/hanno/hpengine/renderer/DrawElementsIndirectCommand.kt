package de.hanno.hpengine.renderer

import de.hanno.hpengine.ElementCount
import struktgen.api.Strukt
import java.nio.ByteBuffer

data class DrawElementsIndirectCommand(
    var count: ElementCount = ElementCount(0),
    var instanceCount: ElementCount = ElementCount(0),
    var firstIndex: ElementCount = ElementCount(0),
    var baseVertex: ElementCount = ElementCount(0),
    var baseInstance: ElementCount = ElementCount(0),
)

interface DrawElementsIndirectCommandStrukt : Strukt {
    context(ByteBuffer) var count: Int
    context(ByteBuffer) var instanceCount: Int
    context(ByteBuffer) var firstIndex: Int
    context(ByteBuffer) var baseVertex: Int
    context(ByteBuffer) var baseInstance: Int

    companion object
}

