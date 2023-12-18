package de.hanno.hpengine.graphics

import de.hanno.hpengine.graphics.output.FinalOutput
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

interface PrimaryRenderer: RenderSystem {
    val finalOutput: FinalOutput
    val priority: Int get() = -1
}

@Single
class RenderSystemsConfig(allRenderSystems: List<RenderSystem>) {
    val allRenderSystems = allRenderSystems.distinct()

    val nonPrimaryRenderers = allRenderSystems.distinct().filterNot { it is PrimaryRenderer }
    val primaryRenderers = allRenderSystems.distinct().filterIsInstance<PrimaryRenderer>().sortedByDescending { it.priority }
    var primaryRenderer = primaryRenderers.first()

    private val renderSystemsEnabled = allRenderSystems.associateWith { true }.toMutableMap()

    init {
        primaryRenderers.filterNot { it == primaryRenderer }.forEach {
            it.enabled = false
        }
    }
    var renderSystems = nonPrimaryRenderers
        private set
    var RenderSystem.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value || primaryRenderer == this
            renderSystems = allRenderSystems.filter { it.enabled }
        }
}