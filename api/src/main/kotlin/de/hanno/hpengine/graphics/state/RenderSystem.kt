package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.graphics.renderer.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.lifecycle.Updatable

interface RenderSystem: Updatable {
    val sharedRenderTarget: BackBufferRenderTarget<*>? get() = null
    val requiresClearSharedRenderTarget: Boolean get() = false
    fun render(renderState: RenderState) { }
    fun renderEditor(renderState: RenderState) { }
    fun afterFrameFinished() { }
    fun extract(renderState: RenderState) { }
}