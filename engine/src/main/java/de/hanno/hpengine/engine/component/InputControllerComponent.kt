package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity
import java.io.Serializable

abstract class InputControllerComponent(entity: Entity) : BaseComponent(entity), Serializable {

    companion object {

        private const val serialVersionUID = 1L
    }
}
