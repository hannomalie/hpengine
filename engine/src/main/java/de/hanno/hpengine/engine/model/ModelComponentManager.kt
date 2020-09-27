package de.hanno.hpengine.engine.model

import de.hanno.hpengine.engine.manager.Manager

class ModelComponentManager: Manager {
    val modelCache = mutableMapOf<String, Model<*>>()
}