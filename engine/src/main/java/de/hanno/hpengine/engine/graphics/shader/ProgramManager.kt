package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.Asset
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource

interface ProgramManager<BACKEND: BackendType> : Manager {
    val gpuContext: GpuContext<BACKEND>

    fun update(deltaSeconds: Float)
    fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource, defines: Defines = Defines()): Int

    @JvmDefault
    fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource) = loadShader(shaderType, shaderSource, Defines())


    fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines = Defines()): ComputeProgram
    fun getComputeProgram(codeSourceAsset: Asset, defines: Defines = Defines()): ComputeProgram = getComputeProgram(codeSourceAsset.toCodeSource(), defines)

    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource? = null,
                   geometryShaderSource: CodeSource? = null,
                   defines: Defines = Defines()): Program

    fun getProgram(vertexShaderAsset: Asset, fragmentShaderAsset: Asset? = null, geometryShaderAsset: Asset? = null, defines: Defines = Defines()): Program = getProgram(
            vertexShaderAsset.toCodeSource(),
            fragmentShaderAsset?.toCodeSource(),
            geometryShaderAsset?.toCodeSource(),
            defines
    )
}