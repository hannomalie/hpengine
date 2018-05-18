package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTargetBuilder
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState

open class DepthPrepassPipeline @JvmOverloads constructor(private val engine: Engine,
                                                          renderer: Renderer,
                                                          useFrustumCulling: Boolean = true,
                                                          useBackfaceCulling: Boolean = true,
                                                          useLineDrawing: Boolean = true,
                                                          renderCam: Camera? = null,
                                                          cullCam: Camera? = renderCam) : GPUFrustumCulledPipeline(engine, renderer, useFrustumCulling, useBackfaceCulling, useLineDrawing, renderCam, cullCam) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", true))
    val depthTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext).add(ColorAttachmentDefinition().setInternalFormat(Pipeline.HIGHZ_FORMAT)).build()
    private val depthPrepassProgramStatic = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "simple_depth.glsl", Defines())
    private val depthPrepassProgramAnimated = engine.programManager.getProgramFromFileNames("first_pass_animated_vertex.glsl", "simple_depth.glsl", Defines())
    override var depthMap = depthTarget.renderedTexture

    fun draw(renderState: RenderState, firstPassResult: FirstPassResult) {
        super.draw(renderState, depthPrepassProgramStatic, depthPrepassProgramAnimated, firstPassResult)
        renderHighZMap()
    }

    override fun beforeDrawStatic(renderState: RenderState, program: Program) {
        setUniforms(renderState, program)
    }

    override fun beforeDrawAnimated(renderState: RenderState, program: Program) {
        setUniforms(renderState, program)
    }

    override val cullCam: Camera?
        get() = getDebugCam()

    private fun getDebugCam(): Camera? {
        val option = engine.sceneManager.scene.getEntities().stream().filter { it -> it is Camera }.map { it -> it as Camera }.findFirst()
        return if (option.isPresent) option.get() else null
    }
    fun setUniforms(renderState: RenderState, program: Program) {

        val camera = cullCam ?: renderCam ?: renderState.camera
        val viewMatrixAsBuffer = camera.entity.viewMatrixAsBuffer
        val projectionMatrixAsBuffer = camera.projectionMatrixAsBuffer
        val viewProjectionMatrixAsBuffer = camera.viewProjectionMatrixAsBuffer

        program.use()
        program.bindShaderStorageBuffer(1, renderState.materialBuffer)
        program.bindShaderStorageBuffer(3, renderState.entitiesBuffer)
        program.setUniform("useRainEffect", if (Config.getInstance().rainEffect.toDouble() == 0.0) false else true)
        program.setUniform("rainEffect", Config.getInstance().rainEffect)
        program.setUniformAsMatrix4("viewMatrix", viewMatrixAsBuffer)
        program.setUniformAsMatrix4("lastViewMatrix", viewMatrixAsBuffer)
        program.setUniformAsMatrix4("projectionMatrix", projectionMatrixAsBuffer)
        program.setUniformAsMatrix4("viewProjectionMatrix", viewProjectionMatrixAsBuffer)
    }
}