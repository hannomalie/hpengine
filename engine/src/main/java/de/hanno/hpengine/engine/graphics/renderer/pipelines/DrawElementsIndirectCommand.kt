package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.struct.Struct

class DrawElementsIndirectCommand : Struct() {
    var count by 0
    var primCount by 0
    var firstIndex by 0
    var baseVertex by 0
    var baseInstance by 0

    companion object {
        private val command = DrawElementsIndirectCommand()
        val sizeInBytes: Int
            get() = command.sizeInBytes
    }
}

