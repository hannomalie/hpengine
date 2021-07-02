package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines

open class GPUOcclusionCulledPipeline @JvmOverloads constructor(
    val config: Config,
    gpuContext: GpuContext<OpenGl>,
    programManager: ProgramManager<OpenGl>,
    deferredRenderingBuffer: DeferredRenderingBuffer,
    useFrustumCulling: Boolean,
    useBackFaceCulling: Boolean,
    useLineDrawing: Boolean
) : GPUFrustumCulledPipeline(config, gpuContext, programManager, deferredRenderingBuffer, useFrustumCulling, useBackFaceCulling, useLineDrawing) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", config.debug.isUseGpuOcclusionCulling))
}