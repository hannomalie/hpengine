package de.hanno.hpengine.engine.manager

interface Manager {
    fun update(deltaSeconds: Float) {}
    fun clear()
    fun onEntityAdded() {}
}