package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.graphics.shader.Shader.ShaderType
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

class GeometryShader private constructor(override var shaderSource: CodeSource, override var id: Int) : Shader {
    override val shaderType: ShaderType = ShaderType.GeometryShader

    companion object {
        fun load(programManager: OpenGlProgramManager, sourceCode: FileBasedCodeSource, defines: Defines = Defines()): GeometryShader {
            return GeometryShader(sourceCode, programManager.loadShader(ShaderType.GeometryShader, sourceCode, defines))
        }
    }
}