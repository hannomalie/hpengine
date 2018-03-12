package de.hanno.hpengine.engine.graphics.light.pointlight

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.ComponentSystem

class PointLightComponentSystem(val engine: Engine) : ComponentSystem<PointLight> {
    override val componentClass = PointLight::class.java
    private val components: MutableList<PointLight> = mutableListOf()

    override fun update(deltaSeconds: Float) {
        components.forEach {
            it.update(deltaSeconds)
        }
    }

    override fun getComponents(): List<PointLight> = components

    override fun create(entity: Entity): PointLight {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addComponent(component: PointLight) {
        components.add(component)
    }

    override fun clear() = components.clear()

}