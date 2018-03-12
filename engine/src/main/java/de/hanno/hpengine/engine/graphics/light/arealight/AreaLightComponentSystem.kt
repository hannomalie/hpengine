package de.hanno.hpengine.engine.graphics.light.arealight

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.ComponentSystem

class AreaLightComponentSystem(val engine: Engine) : ComponentSystem<AreaLight> {
    override val componentClass = AreaLight::class.java
    private val components: MutableList<AreaLight> = mutableListOf()

    override fun getComponents(): List<AreaLight> = components

    override fun create(entity: Entity): AreaLight {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addComponent(component: AreaLight) {
        components.add(component)
    }

    override fun clear() {
        components.clear()
    }
}