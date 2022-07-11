package de.hanno.hpengine.engine.graphics.state

interface RenderStateRecorder {
    fun add(state: RenderState): Boolean
    val states: List<RenderState>
}