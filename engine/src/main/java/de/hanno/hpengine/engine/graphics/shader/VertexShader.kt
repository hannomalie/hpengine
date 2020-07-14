package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

class VertexShader private constructor(override var shaderSource: CodeSource, override var id: Int) : Shader {
    override val shaderType: Shader.ShaderType = Shader.ShaderType.VertexShader

    companion object {
        fun load(programManager: OpenGlProgramManager, sourceCode: FileBasedCodeSource, defines: Defines = Defines()): VertexShader {
            return VertexShader(sourceCode, programManager.loadShader(Shader.ShaderType.VertexShader, sourceCode, defines))
        }
    }
}