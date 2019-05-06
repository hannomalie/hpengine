package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.TextureManager

class SimpleColorRenderer(programManager: ProgramManager<OpenGl>, val textureManager: TextureManager) : AbstractDeferredRenderer(programManager) {
    init {
    }
    val drawlinesExtension = DrawLinesExtension(this, programManager)
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
        simpleColorProgram.setUniform("time", System.currentTimeMillis().toInt())
        simpleColorProgram.setUniform("useParallax", Config.getInstance().isUseParallax)
        simpleColorProgram.setUniform("useSteepParallax", Config.getInstance().isUseSteepParallax)


        for(batch in state.entitiesState.renderBatchesStatic) {
            for(map in batch.materialInfo.maps) {
                gpuContext.bindTexture(map.key.textureSlot, map.value)
                val uniformKey = "has" + map.key.shaderVariableName[0].toUpperCase() + map.key.shaderVariableName.substring(1)
                simpleColorProgram.setUniform(uniformKey, batch.materialInfo.getHasDiffuseMap())
            }
            DrawUtils.draw(gpuContext, state, batch, simpleColorProgram)
        }

        if(Config.getInstance().isDrawBoundingVolumes) {
            drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        }

        if(Config.getInstance().isUseDirectTextureOutput) {
            finalImage = Config.getInstance().directTextureOutputTextureIndex
        } else {
            finalImage = deferredRenderingBuffer.colorReflectivenessMap
        }
        super.render(result, state)
    }
}