package de.hanno.hpengine.graphics.state

interface RenderStateRecorder {
    fun add(state: RenderState): Boolean
    val states: List<RenderState>
}