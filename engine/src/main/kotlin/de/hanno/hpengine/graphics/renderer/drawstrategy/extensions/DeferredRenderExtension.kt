package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import com.artemis.World
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.lifecycle.Updatable

interface DeferredRenderExtension: Updatable {
    val renderPriority get() = -1
    fun renderZeroPass(renderState: RenderState) {}
    fun renderFirstPass(renderState: RenderState) {}
    fun renderSecondPassFullScreen(renderState: RenderState) {}
    fun renderSecondPassHalfScreen(renderState: RenderState) {}
    fun renderEditor(renderState: RenderState) {}
    fun extract(renderState: RenderState, world: World) {}
}
