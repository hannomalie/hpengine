package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.texture.TextureManager

class SimpleColorRenderer(val engineContext: EngineContext<*>, programManager: ProgramManager<OpenGl>,
                          val textureManager: TextureManager) : AbstractDeferredRenderer(programManager, engineContext.config) {
    init {
    }
    val drawlinesExtension = DrawLinesExtension(engineContext, this, programManager)
    val simpleColorProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl")

    override fun render(result: DrawResult, state: RenderState) {
        deferredRenderingBuffer.use(true)

        simpleColorProgram.use()

        val viewMatrixAsBuffer = state.camera.viewMatrixAsBuffer
        val projectionMatrixAsBuffer = state.camera.projectionMatrixAsBuffer
        val viewProjectionMatrixAsBuffer = state.camera.viewProjectionMatrixAsBuffer

        simpleColorProgram.bindShaderStorageBuffer(1, state.materialBuffer)
        simpleColorProgram.bindShaderStorageBuffer(3, state.entitiesBuffer)
        simpleColorProgram.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
        simpleColorProgram.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer)
        simpleColorProgram.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
        simpleColorProgram.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)
        simpleColorProgram.setUniform("eyePosition", state.camera.getPosition())
        simpleColorProgram.setUniform("near", state.camera.getNear())
        simpleColorProgram.setUniform("far", state.camera.getFar())
        simpleColorProgram.setUniform("timeGpu", System.currentTimeMillis().toInt())
        simpleColorProgram.setUniform("useParallax", engineContext.config.quality.isUseParallax)
        simpleColorProgram.setUniform("useSteepParallax", engineContext.config.quality.isUseSteepParallax)


        for(batch in state.entitiesState.renderBatchesStatic) {
            for(map in batch.materialInfo.maps) {
                gpuContext.bindTexture(map.key.textureSlot, map.value)
                val uniformKey = "has" + map.key.shaderVariableName[0].toUpperCase() + map.key.shaderVariableName.substring(1)
                simpleColorProgram.setUniform(uniformKey, batch.materialInfo.getHasDiffuseMap())
            }
            DrawUtils.draw(gpuContext, state, batch, simpleColorProgram, true)
        }

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