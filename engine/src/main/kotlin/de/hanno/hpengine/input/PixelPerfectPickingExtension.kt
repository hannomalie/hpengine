package de.hanno.hpengine.input

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.Format
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.picking.Indices
import de.hanno.hpengine.graphics.renderer.picking.OnClickListener
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.window.Window
import org.joml.Vector2f
import org.joml.Vector2i
import org.koin.core.annotation.Single
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

@Single(binds = [PixelPerfectPickingExtension::class, DeferredRenderExtension::class])
class PixelPerfectPickingExtension(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val input: Input,
    private val listeners: List<OnClickListener>,
    private val window: Window,
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
            graphicsApi.readBuffer(4)
            floatBuffer.rewind()
            val ratio = Vector2f(
                config.width.toFloat() / window.width.toFloat(),
                config.height.toFloat() / window.height.toFloat()
            )
            val adjustedX = (input.getMouseX() * ratio.x).toInt()
            val adjustedY = (input.getMouseY() * ratio.y).toInt()
            graphicsApi.readPixels(adjustedX, adjustedY, 1, 1, Format.RGBA, floatBuffer)
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