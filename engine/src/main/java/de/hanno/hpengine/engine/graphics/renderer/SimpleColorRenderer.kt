package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.SimplePipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setUniforms
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.model.texture.TextureManager

class SimpleColorRenderer(val engineContext: EngineContext<OpenGl>,
                          programManager: ProgramManager<OpenGl> = engineContext.programManager,
                          val textureManager: TextureManager = engineContext.textureManager,
                          deferredRenderingBuffer: DeferredRenderingBuffer) : AbstractDeferredRenderer(programManager, engineContext.config, deferredRenderingBuffer) {
    val drawlinesExtension = DrawLinesExtension(engineContext, this, programManager)
    val simpleColorProgramStatic = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl")
    val simpleColorProgramAnimated = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("ANIMATED", true)))

    val pipeline = engineContext.renderStateManager.renderState.registerState {
        SimplePipeline(engineContext)
    }

    override fun render(result: DrawResult, state: RenderState) {
        deferredRenderingBuffer.use(gpuContext, true)

        simpleColorProgramStatic.setUniforms(state, state.camera, config)

        state[pipeline].draw(state, simpleColorProgramStatic, simpleColorProgramAnimated, result.firstPassResult)

        if(engineContext.config.debug.isDrawBoundingVolumes) {
            drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        }

        if(engineContext.config.debug.isUseDirectTextureOutput) {
            finalImage = engineContext.config.debug.directTextureOutputTextureIndex
        } else {
            finalImage = deferredRenderingBuffer.colorReflectivenessMap
        }
        super.render(result, state)
    }
}

private operator fun <T> RenderState.get(stateRef: StateRef<T>): T = getState(stateRef)
