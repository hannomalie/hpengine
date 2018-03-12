package de.hanno.hpengine.engine.graphics.light.pointlight

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.ComponentSystem
import org.joml.Vector4f

class PointLightComponentSystem(val engine: Engine) : ComponentSystem<PointLight> {
    override val componentClass = PointLight::class.java
    private val components: MutableList<PointLight> = mutableListOf()

    override fun getComponents(): List<PointLight> = components

    override fun create(entity: Entity): PointLight {
        return PointLight(entity, Vector4f(1f,1f,1f,1f), 100f)
    }

    override fun addComponent(component: PointLight) {
        components.add(component)
    }

    override fun clear() = components.clear()

}