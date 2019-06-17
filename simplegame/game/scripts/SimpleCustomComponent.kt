//package scripts

import de.hanno.hpengine.engine.component.CustomComponent
import de.hanno.hpengine.engine.entity.Entity

class SimpleCustomComponent : CustomComponent {
    private lateinit var entity: Entity

    override fun getEntity(): Entity {
        return entity
    }

    override fun init(engine: de.hanno.hpengine.engine.backend.EngineContext<*>) {
        println("Init called in SimpleCustomComponent")
    }

    override fun update(seconds: Float) {
        println("Update called in SimpleCustomComponent")
    }
}