package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable
import de.hanno.hpengine.engine.scene.Scene

interface RenderExtension<TYPE : BackendType>: Updatable {
    @JvmDefault fun renderFirstPass(backend: Backend<TYPE>, gpuContext: GpuContext<TYPE>, firstPassResult: FirstPassResult, renderState: RenderState) {}
    @JvmDefault fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    @JvmDefault fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    @JvmDefault fun renderEditor(renderState: RenderState, result: DrawResult) {}
    @JvmDefault fun extract(scene: Scene, renderState: RenderState) {}
}