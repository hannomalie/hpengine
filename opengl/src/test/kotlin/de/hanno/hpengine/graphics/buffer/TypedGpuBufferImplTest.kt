package de.hanno.hpengine.graphics.buffer

import Float3Impl.Companion.type
import de.hanno.hpengine.Float3
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.graphics.constants.BufferTarget
import de.hanno.hpengine.graphics.createOpenGLContext
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

internal class TypedGpuBufferImplTest {

    @Test
    fun foo() {
        with(createOpenGLContext()) {
            val buffer = PersistentMappedBuffer(second, BufferTarget.ShaderStorage, SizeInBytes(512))
            val typedBuffer = buffer.typed(Float3.type)
            val originalByteBuffer = buffer.buffer

            buffer.ensureCapacityInBytes(SizeInBytes(1024))

            buffer.buffer shouldNotBe originalByteBuffer
            buffer.buffer.capacity() shouldBe 1024

            buffer.buffer shouldBe typedBuffer.buffer
            buffer.buffer shouldBe typedBuffer.gpuBuffer.buffer
        }
    }
}
