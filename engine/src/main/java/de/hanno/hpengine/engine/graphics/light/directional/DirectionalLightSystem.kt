package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.SimpleEntitySystem
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.scene.SimpleScene

class DirectionalLightSystem(engine: Engine, simpleScene: SimpleScene, val eventBus: EventBus): SimpleEntitySystem(engine, simpleScene, listOf(DirectionalLight::class.java)) {

    var directionalLightMovedInCycle: Long = 0

    init {
        eventBus.register(this)
    }

    override fun update(deltaSeconds: Float) {

        getDirectionalLight().update(deltaSeconds)

        if (getDirectionalLight().getEntity().hasMoved()) {
            directionalLightMovedInCycle = engine.getScene().currentCycle
            getDirectionalLight().entity.isHasMoved = false
        }
    }

    fun getDirectionalLight() = getComponents(DirectionalLight::class.java).first()
}
