package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.OpenGlBackend
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines

open class GPUOcclusionCulledPipeline @JvmOverloads constructor(engineContext: EngineContext<OpenGlBackend>,
                                                                renderer: Renderer<OpenGlBackend>,
                                                                useFrustumCulling: Boolean,
                                                                useBackFaceCulling: Boolean,
                                                                useLineDrawing: Boolean,
                                                                renderCam: Camera? = null,
                                                                cullCam: Camera? = renderCam) : GPUFrustumCulledPipeline(engineContext, renderer, useFrustumCulling, useBackFaceCulling, useLineDrawing, renderCam, cullCam) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", Config.getInstance().isUseGpuOcclusionCulling))
}