package de.hanno.hpengine.graphics.editor

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import de.hanno.hpengine.graphics.PrimaryRenderer
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.RenderSystemsConfig
import org.koin.core.annotation.Single

@Single(binds = [BaseSystem::class, BaseEntitySystem::class])
class PrimaryRendererSelection(
    private val renderSystemsConfig: Lazy<RenderSystemsConfig>,
): BaseSystem() {
    private var _primaryRenderer: PrimaryRenderer? = null
    var primaryRenderer
        get() = _primaryRenderer ?: renderSystemsConfig.value.primaryRenderers.filterNot {
            it is ImGuiEditor
        }.maxByOrNull { it.renderPriority }!!
        set(value) {
            _primaryRenderer = value
            renderSystemsConfig.value.run {
                primaryRenderers.forEach {
                    it.enabled = it == value
                }
            }
        }
    override fun processSystem() { }
}