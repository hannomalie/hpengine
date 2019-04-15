package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState

class SimpleLinesRenderer(programManager: ProgramManager<OpenGl>) : AbstractRenderer(programManager) {
    val drawlinesExtension = DrawLinesExtension(this, programManager)

    override fun render(result: DrawResult, state: RenderState) {
        deferredRenderingBuffer.use(true)
        drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        super.render(result, state)
    }
}