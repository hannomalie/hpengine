package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.backend.Backend
import de.hanno.hpengine.graphics.GpuContext

import java.nio.FloatBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.graphics.state.RenderState
import org.joml.Vector2f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.lang.Exception
import java.util.logging.Logger

context(GpuContext)
class PixelPerfectPickingExtension : DeferredRenderExtension {
    private val floatBuffer: FloatBuffer = BufferUtils.createFloatBuffer(4)

    override fun renderFirstPass(
        backend: Backend,
        firstPassResult: FirstPassResult,
        renderState: RenderState
    ) {
        if (backend.input.pickingClick == 1) {
            readBuffer(4)
            floatBuffer.rewind()
            //             TODO: This doesn't make sense anymore, does it?
            val ratio = Vector2f(
                window.width.toFloat() / window.width.toFloat(),
                window.height.toFloat() / window.height.toFloat()
            )
            val adjustedX = (backend.input.getMouseX() * ratio.x).toInt()
            val adjustedY = (backend.input.getMouseY() * ratio.y).toInt()
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer)
            Logger.getGlobal().info("Picked: $adjustedX : $adjustedY")
            try {
                val entityIndexComponentIndex = 0 // red component
                val meshIndexComponentIndex = 3 // alpha component
                val entityIndex = floatBuffer[entityIndexComponentIndex].toInt()
                val meshIndex = floatBuffer[meshIndexComponentIndex].toInt()
                println("Clicked mesh \$meshIndex")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            backend.input.pickingClick = 0
        }
    }
}