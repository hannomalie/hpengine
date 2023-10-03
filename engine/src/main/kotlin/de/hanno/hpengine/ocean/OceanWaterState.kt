package de.hanno.hpengine.ocean

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.EntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.model.MaterialComponent
import org.koin.core.annotation.Single

class OceanWaterState {
    var oceanWaterComponents: MutableList<OceanWaterComponent> = mutableListOf()
    var materialComponents: MutableList<MaterialComponent> = mutableListOf()
    var seconds = 0.0f
}

@All(OceanWaterComponent::class, MaterialComponent::class)
@Single(binds = [OceanWaterSystem::class, BaseSystem::class, RenderSystem::class])
class OceanWaterSystem(renderStateContext: RenderStateContext): RenderSystem, EntitySystem() {
    lateinit var oceanWaterComponentMapper: ComponentMapper<OceanWaterComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>

    val state = renderStateContext.renderState.registerState {
        OceanWaterState()
    }

    private var seconds = 0.0f
    override fun extract(renderState: RenderState) {
        renderState[state].apply {
            val writeState = renderState[state]
            writeState.oceanWaterComponents.clear()
            writeState.materialComponents.clear()
            forEachEntity { entityId ->
                writeState.oceanWaterComponents.add(oceanWaterComponentMapper[entityId]) // TODO: Copy components here, this is a race condition
                writeState.materialComponents.add(materialComponentMapper[entityId]) // TODO: Copy components here, this is a race condition

                this@OceanWaterSystem.seconds += oceanWaterComponentMapper[entityId].timeFactor * renderState.deltaSeconds // TODO: Doesn't work with multiple entities
                this.seconds = this@OceanWaterSystem.seconds
            }
        }
    }

    override fun processSystem() { }
}
