package de.hanno.hpengine.graphics.renderer.drawstrategy.extensions

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.renderer.constants.Format
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.input.MouseClickListener
import org.joml.Vector2f
import org.joml.Vector2i
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

context(GraphicsApi)
class PixelPerfectPickingExtension(
    private val config: Config,
    private val input: Input,
    private val listeners: List<OnClickListener>
) : DeferredRenderExtension {
    override val renderPriority: Int = 1000
    private val floatBuffer: FloatBuffer = BufferUtils.createFloatBuffer(4)

    private val mouseClickListener = MouseClickListener(input)

    override fun update(deltaSeconds: Float) {
        mouseClickListener.update(deltaSeconds)
    }
    override fun renderFirstPass(
        renderState: RenderState
    ) {
        mouseClickListener.consumeClick {
            readBuffer(4)
            floatBuffer.rewind()
            val ratio = Vector2f(
                config.width.toFloat() / window.width.toFloat(),
                config.height.toFloat() / window.height.toFloat()
            )
            val adjustedX = (input.getMouseX() * ratio.x).toInt()
            val adjustedY = (input.getMouseY() * ratio.y).toInt()
            readPixels(adjustedX, adjustedY, 1, 1, Format.RGBA, floatBuffer)
            try {
                val entityId = floatBuffer[0].toInt()
                val entityBufferIndex = floatBuffer[1].toInt()
                val materialIndex = floatBuffer[2].toInt()
                val meshIndex = floatBuffer[3].toInt()
                val indices = Indices(entityBufferIndex, entityId, meshIndex, materialIndex)
                listeners.forEach {
                    it.onClick(Vector2i(adjustedX, adjustedY), indices)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}