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
    val renderPriority: Int get() = -1
}

@Single
class RenderSystemsConfig(allRenderSystems: List<RenderSystem>) {
    val allRenderSystems = allRenderSystems.distinct()
    val nonPrimaryRenderers = allRenderSystems.filterNot { it is PrimaryRenderer }
    val primaryRenderers = allRenderSystems.distinct().filterIsInstance<PrimaryRenderer>().sortedByDescending { it.renderPriority }
    var primaryRenderer = primaryRenderers.first()
        set(value) {
            primaryRenderers.forEach {
                it.enabled = it == value
            }
            field = value
        }

    private val renderSystemsEnabled = allRenderSystems.associateWith { true }.toMutableMap()

    init {
        primaryRenderers.forEach {
            it.enabled = it != primaryRenderer
        }
    }
    var renderSystems = allRenderSystems
        private set
    var RenderSystem.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
            renderSystems = allRenderSystems.filter { it.enabled }
        }
}