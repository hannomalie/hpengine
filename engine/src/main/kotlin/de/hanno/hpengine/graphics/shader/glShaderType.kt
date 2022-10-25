package de.hanno.hpengine.graphics.shader

import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL32
import org.lwjgl.opengl.GL43

val ShaderType.glShaderType: Int get() = when(this) {
    ShaderType.VertexShader -> GL20.GL_VERTEX_SHADER
    ShaderType.TesselationControlShader -> GL43.GL_TESS_CONTROL_SHADER
    ShaderType.TesselationEvaluationShader -> GL43.GL_TESS_EVALUATION_SHADER
    ShaderType.GeometryShader -> GL32.GL_GEOMETRY_SHADER
    ShaderType.FragmentShader -> GL20.GL_FRAGMENT_SHADER
    ShaderType.ComputeShader -> GL43.GL_COMPUTE_SHADER
}