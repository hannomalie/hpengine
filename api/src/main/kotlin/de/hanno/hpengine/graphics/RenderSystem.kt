package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.rendertarget.BackBufferRenderTarget
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.lifecycle.Updatable
import org.koin.core.annotation.Single

interface RenderSystem: Updatable {
    val sharedRenderTarget: BackBufferRenderTarget<*>? get() = null
    val requiresClearSharedRenderTarget: Boolean get() = false
    val supportsSingleStep: Boolean get() = true
    fun render(renderState: RenderState) { }
    fun afterFrameFinished() { }
    fun extract(renderState: RenderState) { }
}

@Single
class RenderSystemsConfig(allRenderSystems: List<RenderSystem>) {
    val allRenderSystems = allRenderSystems.distinct()
    private val renderSystemsEnabled = this.allRenderSystems.associateWith { true }.toMutableMap()
    var renderSystems = this.allRenderSystems
        private set
    var RenderSystem.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
            renderSystems = allRenderSystems.filter { it.enabled }
        }
}