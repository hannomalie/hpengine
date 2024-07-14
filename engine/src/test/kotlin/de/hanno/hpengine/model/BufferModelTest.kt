package de.hanno.hpengine.model

import VertexStruktPackedImpl.Companion.type
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.GameDirectory
import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.scene.*
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import de.hanno.hpengine.toCount
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.joml.Vector4f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import struktgen.api.forEachIndexed
import java.io.File
import java.nio.ByteBuffer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BufferModelTest {
    val config = Config()
    val windowAndContext = createOpenGLContext()
    val window = windowAndContext.first
    val context = windowAndContext.second

    val staticModelLoader = StaticModelLoader()

    val cube = staticModelLoader.load(
        "assets/models/cube.obj",
        OpenGLTextureManager(config, context, OpenGlProgramManager(context, null, config)),
        GameDirectory(File("src/main/resources/hp"), null)
    )
    @AfterAll
    fun adteAll() {
        window.close()
    }

    @Nested
    inner class VertexIndexBufferTest {
        @Test
        fun `adds single model to geometry buffer`() {

            val geometryBuffer = VertexIndexBuffer(context, VertexStruktPacked.type, 1)
            val startOffsets = geometryBuffer.allocate(cube)
            val offsetsForMeshes = cube.putToBuffer(geometryBuffer, startOffsets)

            geometryBuffer.indexBuffer.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 12 * 3 * Int.SIZE_BYTES
            geometryBuffer.vertexStructArray.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 24 * VertexStruktPacked.type.sizeInBytes
            startOffsets.vertexOffset.value.toInt() shouldBeExactly 0
            offsetsForMeshes shouldHaveSize 1
            offsetsForMeshes[0] shouldBeEqualToComparingFields startOffsets
            geometryBuffer.currentVertex.value.toInt() shouldBe 24
            geometryBuffer.currentIndex.value.toInt() shouldBe 12 * 3

            val expectedPositions = cube.uniqueVertices.map {
                Vector4f(it.position.x(), it.position.y(), it.position.z(), 1.0f)
            }
            geometryBuffer.vertexStructArray.forEachIndexed { index, it ->
                withClue("Position not like expected at index $index") {
                    it.position.toJoml() shouldBe expectedPositions[index]
                }
            }
        }

        @Test
        fun `adds multiple models to geometry buffer`() {
            val geometryBuffer = VertexIndexBuffer(context, VertexStruktPacked.type, 1)
            cube.putToBuffer(geometryBuffer)
            geometryBuffer.currentVertex.value.toInt() shouldBeExactly cube.uniqueVertices.size
            geometryBuffer.currentIndex.value.toInt() shouldBeExactly cube.indicesCount.value.toInt()
            val offsetsForSecondModel = cube.putToBuffer(geometryBuffer)[0]

            geometryBuffer.indexBuffer.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 16 * Int.SIZE_BYTES
            geometryBuffer.vertexStructArray.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 16 * VertexStruktPacked.type.sizeInBytes
            offsetsForSecondModel shouldBeEqualToComparingFields VertexIndexOffsets(24.toCount(), 36.toCount())
        }
    }
    @Nested
    inner class VertexBufferTest {
        @Test
        fun `adds single model to geometry buffer`() {
            val geometryBuffer = VertexBuffer(context, VertexStruktPacked.type)
            val offsetsForMeshes = cube.putToBuffer(geometryBuffer)
            geometryBuffer.currentVertex.value.toInt() shouldBeExactly cube.triangleCount.value.toInt() * 3

            geometryBuffer.vertexStructArray.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 36 * VertexStruktPacked.type.sizeInBytes
            offsetsForMeshes shouldHaveSize 1
        }

        @Test
        fun `adds multiple models to geometry buffer`() {
            val geometryBuffer = VertexBuffer(context, VertexStruktPacked.type)
            cube.putToBuffer(geometryBuffer)
            geometryBuffer.currentVertex.value.toInt() shouldBeExactly cube.triangleCount.value.toInt() * 3
            val offsetsForSecondModel = cube.putToBuffer(geometryBuffer)[0]

            geometryBuffer.vertexStructArray.sizeInBytes.value.toInt() shouldBeGreaterThanOrEqual 16 * VertexStruktPacked.type.sizeInBytes
            offsetsForSecondModel shouldBeEqualToComparingFields VertexOffsets(cube.triangleCount * 3)
        }
    }
}

context(ByteBuffer)
private fun Vector4fStrukt.toJoml() = Vector4f(x, y, z, w)

fun createOpenGLContext(
    config: Config = Config(),
): Pair<GlfwWindow, OpenGLContext> {
    val profiler = OpenGLGPUProfiler(config.debug::profiling)
    val window = GlfwWindow(
        config,
        profiler,
        visible = false,
    )
    return Pair(
        window, OpenGLContext(
            window,
            config
        )
    )
}