package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderType
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

class ComputeShader(override var shaderSource: CodeSource, override var id: Int = -1) : Shader {
    override val shaderType: ShaderType = ShaderType.ComputeShader

    companion object {
        @JvmOverloads
        fun load(programManager: OpenGlProgramManager, sourceCode: FileBasedCodeSource, defines: Defines = Defines()): ComputeShader {
            return ComputeShader(sourceCode, programManager.loadShader(ShaderType.ComputeShader, sourceCode, defines))
        }
    }
}