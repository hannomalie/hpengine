package de.hanno.hpengine.editor.window

import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGlExecutor
import de.hanno.hpengine.engine.graphics.Window

class AWTEditorWindow(
    val config: ConfigImpl,
    val executor: OpenGlExecutorImpl,
    val frame: RibbonEditor
) : Window<OpenGl>, OpenGlExecutor by executor {

    override var handle: Long = 0
        private set

    val canvas = frame.canvas

    override var title
        get() = frame.title
        set(value) {
            frame.title = value
        }
    override var width: Int
        get() = frame.width
        set(value) {
            frame.size.width = value
        }
    override var height: Int
        get() = frame.height
        set(value) {
            frame.size.height = value
        }

    override val vSync = false

    override fun getCursorPosition(mouseX: DoubleArray, mouseY: DoubleArray) {

    }

    override fun getFrameBufferSize(width: IntArray, height: IntArray) {
        width[0] = this.width
        height[0] = this.height
    }

    override fun getKey(keyCode: Int): Int {
        return 0
    }

    override fun getMouseButton(buttonCode: Int): Int {
        return 0
    }

    override fun showWindow() {}

    override fun hideWindow() {}

    override fun pollEvents() {}

    override val frontBuffer = canvas.createFrontBufferRenderTarget()

    override fun swapBuffers() = withLockedCanvas {
        canvas.swapBuffers()
    }

    override fun setVSync(vSync: Boolean, gpuContext: GpuContext<OpenGl>) {
    }

    private inline fun <T> withLockedCanvas(block: () -> T): T = try {
        canvas.canvas.lock()
        block()
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    } finally {
        canvas.canvas.unlock()
    }

    override fun awaitEvents() {

    }
}