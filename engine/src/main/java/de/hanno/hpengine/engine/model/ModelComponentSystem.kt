package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.buffer.Bufferable
import de.hanno.hpengine.engine.manager.ComponentSystem

class ModelComponentSystem(val engine: Engine) : ComponentSystem<ModelComponent> {
    override val components = mutableListOf<ModelComponent>()

    override fun create(entity: Entity) = ModelComponent(entity)

    override fun update(deltaSeconds: Float) {
        for (component in components) {
            component.update(engine, deltaSeconds)
        }
    }

    fun <T: Bufferable> create(entity: Entity, model: Model<T>) = ModelComponent(entity, model)

    override fun addComponent(component: ModelComponent) {
        components.add(component)
    }

}