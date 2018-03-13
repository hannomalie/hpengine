package de.hanno.hpengine.engine.manager

import de.hanno.hpengine.engine.entity.Entity

interface Manager {
    fun update(deltaSeconds: Float) {}
    fun clear()
    fun onEntityAdded(entities: List<Entity>) {}
}