package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setUniforms
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef

class SimpleColorRenderer(val engineContext: EngineContext<OpenGl>): RenderSystem, EngineContext<OpenGl> by engineContext {
    val drawlinesExtension = DrawLinesExtension(engineContext, programManager)
    val simpleColorProgramStatic = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl")
    val simpleColorProgramAnimated = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("ANIMATED", true)))

    val textureRenderer = SimpleTextureRenderer(engineContext, deferredRenderingBuffer.colorReflectivenessTexture)

    val pipeline = engineContext.renderStateManager.renderState.registerState {
        SimplePipeline(engineContext)
    }

    override fun render(result: DrawResult, state: RenderState) {
        deferredRenderingBuffer.use(gpuContext, true)

        simpleColorProgramStatic.setUniforms(state, state.camera, config)

        if(engineContext.config.debug.isDrawBoundingVolumes) {
            drawlinesExtension.renderFirstPass(engineContext, gpuContext, result.firstPassResult, state)
        } else {
            state[pipeline].draw(state, simpleColorProgramStatic, simpleColorProgramAnimated, result.firstPassResult)

        }

        val finalImage = if(engineContext.config.debug.isUseDirectTextureOutput) {
            engineContext.config.debug.directTextureOutputTextureIndex
        } else {
            deferredRenderingBuffer.colorReflectivenessMap
        }

        textureRenderer.drawToQuad(engineContext.window.frontBuffer, finalImage)
    }
}

private operator fun <T> RenderState.get(stateRef: StateRef<T>): T = getState(stateRef)
