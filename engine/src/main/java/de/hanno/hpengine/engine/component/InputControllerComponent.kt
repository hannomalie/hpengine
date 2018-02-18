package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import java.io.Serializable

abstract class InputControllerComponent(entity: Entity) : BaseComponent(), Serializable {
    init {
        this.entity = entity
    }

    override fun getIdentifier(): String {
        return "InputControllerComponent"
    }

    companion object {

        private const val serialVersionUID = 1L
    }
}
