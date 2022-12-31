package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.lifecycle.Updatable

interface RenderSystem: Updatable {
    val sharedRenderTarget: BackBufferRenderTarget<*>? get() = null
    val requiresClearSharedRenderTarget: Boolean get() = false
    fun render(renderState: RenderState) { }
    fun afterFrameFinished() { }
    fun extract(renderState: RenderState) { }
}