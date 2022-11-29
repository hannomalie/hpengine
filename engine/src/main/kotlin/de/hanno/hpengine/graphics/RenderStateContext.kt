package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.multithreading.TripleBuffer

class RenderStateContext(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory) { currentStaging, currentRead ->
        currentStaging.cycle < currentRead.cycle
    }
}