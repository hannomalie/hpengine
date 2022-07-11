package de.hanno.hpengine.engine.system

import de.hanno.hpengine.engine.graphics.state.RenderState

interface Clearable {
    fun clear()
}
interface Extractor {
    fun extract(currentWriteState: RenderState)
}