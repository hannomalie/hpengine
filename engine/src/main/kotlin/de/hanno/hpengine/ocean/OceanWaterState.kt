package de.hanno.hpengine.ocean

import com.artemis.BaseSystem
import com.artemis.ComponentMapper
import com.artemis.EntitySystem
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.light.point.PointLightComponent
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.model.MaterialComponent
import org.koin.core.annotation.Single

class OceanWaterState {
    var oceanWaterComponents: MutableList<OceanWaterComponent> = mutableListOf()
    var materialComponents: MutableList<MaterialComponent> = mutableListOf()
    var oceanSurfaceComponent: MutableList<OceanSurfaceComponent> = mutableListOf()
}

@All(OceanWaterComponent::class, MaterialComponent::class, OceanSurfaceComponent::class)
@Single(binds = [OceanWaterSystem::class, BaseSystem::class, RenderSystem::class])
class OceanWaterSystem(renderStateContext: RenderStateContext): RenderSystem, EntitySystem() {
    lateinit var oceanWaterComponentMapper: ComponentMapper<OceanWaterComponent>
    lateinit var materialComponentMapper: ComponentMapper<MaterialComponent>
    lateinit var oceanSurfaceComponentMapper: ComponentMapper<OceanSurfaceComponent>

    val state = renderStateContext.renderState.registerState {
        OceanWaterState()
    }

    override fun extract(renderState: RenderState) {
        renderState[state].apply {
            val writeState = renderState[state]
            writeState.oceanWaterComponents.clear()
            writeState.materialComponents.clear()
            writeState.oceanSurfaceComponent.clear()
            forEachEntity { entityId ->
                writeState.oceanWaterComponents.add(oceanWaterComponentMapper[entityId]) // TODO: Copy components here, this is a race condition
                writeState.materialComponents.add(materialComponentMapper[entityId]) // TODO: Copy components here, this is a race condition
                writeState.oceanSurfaceComponent.add(oceanSurfaceComponentMapper[entityId]) // TODO: Copy components here, this is a race condition
            }
        }
    }

    override fun processSystem() { }
}
