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

interface DeferredRenderExtension<TYPE : BackendType>: Updatable {
    fun renderFirstPass(backend: Backend<TYPE>, gpuContext: GpuContext<TYPE>, firstPassResult: FirstPassResult, renderState: RenderState) {}
    fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {}
    fun renderEditor(renderState: RenderState, result: DrawResult) {}
    fun extract(scene: Scene, renderState: RenderState) {}
    fun beforeSetScene(nextScene: Scene) {}
    fun afterSetScene(nextScene: Scene) {}
}

open class CompoundExtension<TYPE : BackendType>(val extensions: List<DeferredRenderExtension<TYPE>>): DeferredRenderExtension<TYPE> {
    override fun renderFirstPass(backend: Backend<TYPE>, gpuContext: GpuContext<TYPE>, firstPassResult: FirstPassResult, renderState: RenderState) {
        extensions.forEach { it.renderFirstPass(backend, gpuContext, firstPassResult, renderState) }
    }
    override fun renderSecondPassFullScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        extensions.forEach { it.renderSecondPassFullScreen(renderState, secondPassResult) }
    }
    override fun renderSecondPassHalfScreen(renderState: RenderState, secondPassResult: SecondPassResult) {
        extensions.forEach { it.renderSecondPassHalfScreen(renderState, secondPassResult) }
    }
    override fun renderEditor(renderState: RenderState, result: DrawResult) {
        extensions.forEach { it.renderEditor(renderState, result) }
    }
    override fun extract(scene: Scene, renderState: RenderState) {
        extensions.forEach { it.extract(scene, renderState) }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        extensions.forEach { it.update(scene, deltaSeconds) }
    }
    companion object {
        operator fun <TYPE : BackendType> invoke(vararg extension: DeferredRenderExtension<TYPE>) = CompoundExtension(extension.toList())
    }
}