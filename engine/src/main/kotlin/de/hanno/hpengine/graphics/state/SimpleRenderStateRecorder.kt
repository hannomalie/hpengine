package de.hanno.hpengine.graphics.state

import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.input.Input
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList


context(GpuContext)
class SimpleRenderStateRecorder(private val input: Input) : RenderStateRecorder {
    override val states = CopyOnWriteArrayList<RenderState>()
    override fun add(state: RenderState): Boolean {
        if (input.isKeyPressed(GLFW.GLFW_KEY_R)) {
            keyRWasPressed = true
            println("Pressed")
        } else if (keyRWasPressed) {
            keyRWasPressed = false
            println("Released")
            return states.add(RenderState(state))
        }
        return false
    }

    var keyRWasPressed = false
}