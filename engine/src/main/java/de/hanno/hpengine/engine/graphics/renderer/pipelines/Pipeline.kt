package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CoarseCullingPhase.ONE
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase.*
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.lwjgl.opengl.*

interface Pipeline {
    fun prepareAndDraw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult)
    fun draw(renderState: RenderState, programStatic: Program, programAnimated: Program, firstPassResult: FirstPassResult)
    fun prepare(renderState: RenderState)

    enum class CullingPhase(val coarsePhase: CoarseCullingPhase) {
        STATIC_ONE(ONE),
        STATIC_TWO(CoarseCullingPhase.TWO),
        ANIMATED_ONE(ONE),
        ANIMATED_TWO(CoarseCullingPhase.TWO)
    }

    enum class CoarseCullingPhase {
        ONE,
        TWO
    }

    val CoarseCullingPhase.staticPhase: CullingPhase
        get() = if(this == ONE) STATIC_ONE else STATIC_TWO
    val CoarseCullingPhase.animatedPhase: CullingPhase
        get() = if(this == ONE) ANIMATED_ONE else ANIMATED_TWO

    companion object {
        val HIGHZ_FORMAT = GL30.GL_R32F

        inline fun <reified T> create(useFrustumCulling: Boolean,
                                      useBackfaceCulling: Boolean,
                                      useLineDrawingIfActivated: Boolean,
                                      renderCam: Camera? = null,
                                      cullCam: Camera? = renderCam): Pipeline {
            return when(T::class) {
                is GPUFrustumCulledPipeline -> GPUFrustumCulledPipeline(useFrustumCulling, useBackfaceCulling, useLineDrawingIfActivated, renderCam, cullCam)
                is GPUOcclusionCulledPipeline -> GPUOcclusionCulledPipeline(useFrustumCulling, useBackfaceCulling, useLineDrawingIfActivated, renderCam, cullCam)
                else -> SimplePipeline(useFrustumCulling, useBackfaceCulling, useLineDrawingIfActivated)
            }
        }
    }
}