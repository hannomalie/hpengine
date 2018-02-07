package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.Engine

interface Manager {
    val engine: Engine
    fun update(deltaSeconds: Float) {}
}