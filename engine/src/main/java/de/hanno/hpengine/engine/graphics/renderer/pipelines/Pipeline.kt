package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CoarseCullingPhase.ONE
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase.ANIMATED_ONE
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase.ANIMATED_TWO
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase.STATIC_ONE
import de.hanno.hpengine.engine.graphics.renderer.pipelines.Pipeline.CullingPhase.STATIC_TWO
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.state.RenderState
import org.lwjgl.opengl.GL30

interface Pipeline {
    fun prepare(renderState: RenderState)
    fun draw(renderState: RenderState, programStatic: Program<StaticFirstPassUniforms>, programAnimated: Program<AnimatedFirstPassUniforms>, firstPassResult: FirstPassResult)

    fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {}
    fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {}


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
    }
}