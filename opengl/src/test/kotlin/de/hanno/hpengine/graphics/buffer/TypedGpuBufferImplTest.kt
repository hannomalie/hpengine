package de.hanno.hpengine.graphics.buffer

import Float3Impl.Companion.type
import de.hanno.hpengine.Float3
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.renderer.constants.BufferTarget
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

internal class TypedGpuBufferImplTest {

    @Test
    fun foo() {
        val config = mockk<Config> {
            every { debug } returns mockk {
            }
        }
        val graphicsApi = OpenGLContext.invoke(
            OpenGLGPUProfiler(config),
            GlfwWindow(
                width = 800,
                height = 600,
                title = this.javaClass.simpleName
            ).apply { hideWindow() },
            config
        )

        with(graphicsApi) {
            val buffer = PersistentMappedBuffer(512, BufferTarget.ShaderStorage)
            val typedBuffer = buffer.typed(Float3.type)
            val originalByteBuffer = buffer.buffer

            buffer.ensureCapacityInBytes(1024)

            buffer.buffer shouldNotBe originalByteBuffer
            buffer.buffer.capacity() shouldBe 1024

            buffer.buffer shouldBe typedBuffer.buffer
            buffer.buffer shouldBe typedBuffer.gpuBuffer.buffer
        }
    }
}
