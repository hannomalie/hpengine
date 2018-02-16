package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.model.Entity
import java.io.Serializable

abstract class InputControllerComponent(entity: Entity) : BaseComponent(), Serializable {
    init {
        this.entity = entity
    }

    override fun getIdentifier(): String {
        return "InputControllerComponent"
    }

    override fun isInitialized(): Boolean {
        return true
    }

    companion object {

        private const val serialVersionUID = 1L
    }
}
