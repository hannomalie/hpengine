package de.hanno.hpengine.system

import de.hanno.hpengine.graphics.state.RenderState

interface Clearable {
    fun clear()
}
interface Extractor {
    fun extract(currentWriteState: RenderState)
}