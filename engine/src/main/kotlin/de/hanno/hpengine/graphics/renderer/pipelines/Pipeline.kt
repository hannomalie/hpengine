package de.hanno.hpengine.graphics.renderer.pipelines

import de.hanno.hpengine.camera.Camera
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.shader.Program
import de.hanno.hpengine.graphics.state.RenderState
import org.lwjgl.opengl.GL30

interface Pipeline {
    fun prepare(renderState: RenderState)
    fun draw(renderState: RenderState, programStatic: Program<StaticFirstPassUniforms>, programAnimated: Program<AnimatedFirstPassUniforms>, firstPassResult: FirstPassResult)

    fun beforeDrawStatic(renderState: RenderState, program: Program<StaticFirstPassUniforms>, renderCam: Camera) {}
    fun beforeDrawAnimated(renderState: RenderState, program: Program<AnimatedFirstPassUniforms>, renderCam: Camera) {}

    companion object {
        val HIGHZ_FORMAT = GL30.GL_R32F
    }
}
