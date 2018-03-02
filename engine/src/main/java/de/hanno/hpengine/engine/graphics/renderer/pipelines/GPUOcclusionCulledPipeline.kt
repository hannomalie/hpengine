package de.hanno.hpengine.engine.graphics.renderer.pipelines

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.graphics.renderer.Renderer
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines

open class GPUOcclusionCulledPipeline @JvmOverloads constructor(private val engine: Engine,
                                                                renderer: Renderer,
                                                                useFrustumCulling: Boolean = true,
                                                                useBackfaceCulling: Boolean = true,
                                                                useLineDrawing: Boolean = true,
                                                                renderCam: Camera? = null,
                                                                cullCam: Camera? = renderCam) : GPUFrustumCulledPipeline(engine, renderer, useFrustumCulling, useBackfaceCulling, useLineDrawing, renderCam, cullCam) {
    override fun getDefines() = Defines(Define.getDefine("FRUSTUM_CULLING", true), Define.getDefine("OCCLUSION_CULLING", true))
}