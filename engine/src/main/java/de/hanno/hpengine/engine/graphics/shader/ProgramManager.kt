package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.util.ressources.CodeSource

interface ProgramManager<BACKEND: BackendType> : Manager {
    val gpuContext: GpuContext<BACKEND>

    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?, defines: Defines): Program
    @JvmDefault
    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?)
            = getProgramFromFileNames(vertexShaderFilename, fragmentShaderFileName, Defines())
    @JvmDefault
    fun getProgramFromFileNames(vertexShaderFilename: String)
            = getProgramFromFileNames(vertexShaderFilename, null, Defines())

    fun getComputeProgram(computeShaderLocation: String, defines: Defines): ComputeShaderProgram
    @JvmDefault
    fun getComputeProgram(computeShaderLocation: String) = getComputeProgram(computeShaderLocation, Defines())

    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?,
                   geometryShaderSource: CodeSource?,
                   defines: Defines = Defines()): Program

    @JvmDefault
    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?,
                   geometryShaderSource: CodeSource?) = getProgram(vertexShaderSource, fragmentShaderSource, geometryShaderSource, Defines())

    @JvmDefault
    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?) = getProgram(vertexShaderSource, fragmentShaderSource, null, Defines())
    @JvmDefault
    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?, defines: Defines) = getProgram(vertexShaderSource, fragmentShaderSource, null, defines)
    @JvmDefault
    fun getProgram(vertexShaderSource: CodeSource) = getProgram(vertexShaderSource, null, null, Defines())



    fun <SHADERTYPE : Shader> loadShader(type: Class<SHADERTYPE>, shaderSource: CodeSource, defines: Defines = Defines()): SHADERTYPE
    @JvmDefault
    fun <SHADERTYPE : Shader> loadShader(type: Class<SHADERTYPE>, shaderSource: CodeSource) = loadShader(type, shaderSource, Defines())

}