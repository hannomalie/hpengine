package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer

class RenderStateManager(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory,
            { currentStaging, currentRead -> currentStaging.cycle < currentRead.cycle })
}