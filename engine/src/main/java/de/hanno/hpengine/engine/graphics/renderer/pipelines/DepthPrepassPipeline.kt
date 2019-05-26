package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
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

open class DepthPrepassPipeline @JvmOverloads constructor(private val engine: Engine<OpenGl>,
                                                          renderer: Renderer<OpenGl>,
                                                          useFrustumCulling: Boolean = true,
                                                          useBackFaceCulling: Boolean = true,
                                                          useLineDrawing: Boolean = true,
                                                          renderCam: Camera? = null,
                                                          cullCam: Camera? = renderCam) : GPUFrustumCulledPipeline(engine, renderer, useFrustumCulling, useBackFaceCulling, useLineDrawing, renderCam, cullCam) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", Config.getInstance().isUseGpuOcclusionCulling))
    val depthTarget = RenderTargetBuilder<RenderTargetBuilder<*,*>, RenderTarget>(engine.gpuContext)
                    .setName("DepthPrepassPipeline")
                    .add(ColorAttachmentDefinition("Depth")
                    .setInternalFormat(Pipeline.HIGHZ_FORMAT))
                    .build()
    private val depthPrepassProgramStatic = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "simple_depth.glsl", Defines())
    private val depthPrepassProgramAnimated = engine.programManager.getProgramFromFileNames("first_pass_animated_vertex.glsl", "simple_depth.glsl", Defines())
    override var depthMap = depthTarget.renderedTexture

    fun draw(renderState: RenderState, firstPassResult: FirstPassResult) {
        super.draw(renderState, depthPrepassProgramStatic, depthPrepassProgramAnimated, firstPassResult)
        renderHighZMap()
    }

    override val cullCam: Camera?
        get() = getDebugCam()

    private fun getDebugCam(): Camera? {
        val option = engine.sceneManager.scene.entityManager.getEntities().stream().filter { it is Camera }.map { it as Camera }.findFirst()
        return if (option.isPresent) option.get() else null
    }
}