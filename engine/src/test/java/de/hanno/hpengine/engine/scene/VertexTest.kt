package de.hanno.hpengine.engine.scene

import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.Assert
import org.junit.Test

class VertexTest {
    @Test
    fun testStructOutput() {
        val vertex = Vertex("Vertex", Vector3f(), Vector2f(), Vector3f())

        val structCode = vertex.structCode()
        Assert.assertEquals("struct Vertex {\n" +
                "vec3 position;\n" +
                "vec2 texCoord;\n" +
                "vec3 normal;\n" +
                "}", structCode)
    }
}