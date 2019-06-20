package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity

import java.io.Serializable

abstract class BaseComponent(override val entity: Entity) : Component, Serializable {

    override val identifier: String
        get() = this.javaClass.toString() + " " + System.currentTimeMillis()

    companion object {
        private const val serialVersionUID = -224913983270697337L
    }

}
