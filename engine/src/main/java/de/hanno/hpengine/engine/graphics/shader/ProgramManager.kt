package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.util.ressources.FileBasedCodeSource

interface ProgramManager<BACKEND: BackendType> : Manager {
    val gpuContext: GpuContext<BACKEND>

    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?, defines: Defines): Program
    @JvmDefault
    fun getProgramFromFileNames(vertexShaderFilename: String, fragmentShaderFileName: String?)
            = getProgramFromFileNames(vertexShaderFilename, fragmentShaderFileName, Defines())
    @JvmDefault
    fun getProgramFromFileNames(vertexShaderFilename: String)
            = getProgramFromFileNames(vertexShaderFilename, null, Defines())

    fun getComputeProgram(computeShaderLocation: String, defines: Defines): ComputeProgram
    @JvmDefault
    fun getComputeProgram(computeShaderLocation: String) = getComputeProgram(computeShaderLocation, Defines())

    fun getProgram(vertexShaderSource: FileBasedCodeSource,
                   fragmentShaderSource: FileBasedCodeSource?,
                   geometryShaderSource: FileBasedCodeSource?,
                   defines: Defines = Defines()): Program

    @JvmDefault
    fun getProgram(vertexShaderSource: FileBasedCodeSource,
                   fragmentShaderSource: FileBasedCodeSource?,
                   geometryShaderSource: FileBasedCodeSource?) = getProgram(vertexShaderSource, fragmentShaderSource, geometryShaderSource, Defines())

    @JvmDefault
    fun getProgram(vertexShaderSource: FileBasedCodeSource,
                   fragmentShaderSource: FileBasedCodeSource?) = getProgram(vertexShaderSource, fragmentShaderSource, null, Defines())
    @JvmDefault
    fun getProgram(vertexShaderSource: FileBasedCodeSource,
                   fragmentShaderSource: FileBasedCodeSource?, defines: Defines) = getProgram(vertexShaderSource, fragmentShaderSource, null, defines)
    @JvmDefault
    fun getProgram(vertexShaderSource: FileBasedCodeSource) = getProgram(vertexShaderSource, null, null, Defines())


    fun loadShader(shadertype: Shader.ShaderType, shaderSource: FileBasedCodeSource, defines: Defines = Defines()): Int

    @JvmDefault
    fun loadShader(shadertype: Shader.ShaderType, shaderSource: FileBasedCodeSource) = loadShader(shadertype, shaderSource, Defines())

}