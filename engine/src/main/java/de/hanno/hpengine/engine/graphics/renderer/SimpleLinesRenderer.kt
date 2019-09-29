package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState

class SimpleLinesRenderer(engineContext: EngineContext<*>,
                          programManager: ProgramManager<OpenGl>,
                          deferredRenderingBuffer: DeferredRenderingBuffer) : AbstractDeferredRenderer(programManager, engineContext.config, deferredRenderingBuffer) {
    val drawlinesExtension = DrawLinesExtension(engineContext, this, programManager)

    override fun render(result: DrawResult, state: RenderState) {
        finalImage = deferredRenderingBuffer.colorReflectivenessMap
        deferredRenderingBuffer.use(gpuContext, true)
        drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        super.render(result, state)
    }
}
