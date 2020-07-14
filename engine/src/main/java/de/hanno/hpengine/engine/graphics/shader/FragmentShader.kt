package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderType
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

class FragmentShader private constructor(override var shaderSource: CodeSource, override var id: Int) : Shader {
    init {
        require(id > 0) { "Invalid id for shader ${shaderSource.name}" }
    }
    override val shaderType: ShaderType = ShaderType.FragmentShader

    companion object {
        fun load(programManager: OpenGlProgramManager, sourceCode: FileBasedCodeSource, defines: Defines = Defines()): FragmentShader {
            return FragmentShader(sourceCode, programManager.loadShader(ShaderType.FragmentShader, sourceCode, defines))
        }
    }
}