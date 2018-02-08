package de.hanno.hpengine.engine.component

import java.io.Serializable

abstract class InputControllerComponent : BaseComponent(), Serializable {

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
