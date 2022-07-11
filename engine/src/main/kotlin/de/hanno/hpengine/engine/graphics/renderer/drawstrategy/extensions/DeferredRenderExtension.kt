package de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions

import com.artemis.World
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.SecondPassResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.lifecycle.Updatable

interface DeferredRenderExtension<TYPE : BackendType>: Updatable {
    fun renderZeroPass(renderState: RenderState) {}
    fun renderFirstPass(backend: Backend<TYPE>, gpuContext: GpuContext<TYPE>, firstPassResult: FirstPassResult, renderState: RenderState) {}
    fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    fun renderEditor(renderState: RenderState, result: DrawResult) {}
    fun extract(renderState: RenderState, world: World) {}
}
