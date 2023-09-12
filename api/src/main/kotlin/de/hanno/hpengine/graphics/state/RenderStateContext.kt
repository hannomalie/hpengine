package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.state.multithreading.TripleBuffer
import org.koin.core.annotation.Single

@Single
class RenderStateContext(
    private val graphicsApi: GraphicsApi
) {
    private val renderStateFactory = { RenderState(graphicsApi) }
    val renderState: TripleBuffer = TripleBuffer(renderStateFactory) { currentStaging, currentRead ->
        currentStaging.cycle < currentRead.cycle
    }
}