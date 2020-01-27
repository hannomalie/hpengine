package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines

open class GPUOcclusionCulledPipeline @JvmOverloads constructor(val engineContext: EngineContext<OpenGl>,
                                                                useFrustumCulling: Boolean,
                                                                useBackFaceCulling: Boolean,
                                                                useLineDrawing: Boolean) : GPUFrustumCulledPipeline(engineContext, useFrustumCulling, useBackFaceCulling, useLineDrawing) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", engineContext.config.debug.isUseGpuOcclusionCulling))
}