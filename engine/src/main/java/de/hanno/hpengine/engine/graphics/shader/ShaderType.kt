package de.hanno.hpengine.engine.graphics.shader

import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL43

enum class ShaderType constructor(val glShaderType: Int) {
    VertexShader(GL20.GL_VERTEX_SHADER),
    FragmentShader(GL20.GL_FRAGMENT_SHADER),
    GeometryShader(GL32.GL_GEOMETRY_SHADER),
    ComputeShader(GL43.GL_COMPUTE_SHADER)
}