package de.hanno.hpengine.engine.graphics.renderer

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawUtils
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.model.material.SimpleMaterial
import de.hanno.hpengine.engine.model.texture.TextureManager
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

class SimpleLinesRenderer(programManager: ProgramManager<OpenGl>) : AbstractRenderer(programManager) {
    init {
        Config.getInstance().isDrawBoundingVolumes = true
    }
    val drawlinesExtension = DrawLinesExtension(this, programManager)

    override fun render(result: DrawResult, state: RenderState) {
        finalImage = deferredRenderingBuffer.colorReflectivenessMap
        deferredRenderingBuffer.use(true)
        drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        super.render(result, state)
    }
}
class SimpleColorRenderer(programManager: ProgramManager<OpenGl>, val textureManager: TextureManager) : AbstractRenderer(programManager) {
    init {
        Config.getInstance().isDrawBoundingVolumes = true
    }
    val drawlinesExtension = DrawLinesExtension(this, programManager)
    val simpleColorProgram = programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_simple_color_fragment.glsl")

    override fun render(result: DrawResult, state: RenderState) {
        finalImage = deferredRenderingBuffer.colorReflectivenessMap
//        gpuContext.clearColor(1f,0f,0f,0f)
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
            if(batch.materialInfo.getHasDiffuseMap()) {
                gpuContext.bindTexture(0, batch.materialInfo.maps[SimpleMaterial.MAP.DIFFUSE]!!)
            } else {
                gpuContext.bindTexture(0, textureManager.defaultTexture)
            }
            DrawUtils.draw(gpuContext, state, batch, simpleColorProgram)
        }

        if(Config.getInstance().isDrawBoundingVolumes) {
            drawlinesExtension.renderFirstPass(null, gpuContext, result.firstPassResult, state)
        }

        super.render(result, state)
    }
}