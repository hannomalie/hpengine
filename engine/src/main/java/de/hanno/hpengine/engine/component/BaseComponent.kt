package de.hanno.hpengine.engine.component

import de.hanno.hpengine.engine.entity.Entity

import java.io.Serializable

abstract class BaseComponent(override val entity: Entity) : Component
