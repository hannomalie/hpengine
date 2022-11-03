package de.hanno.hpengine.graphics.shader

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Asset
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource

interface ProgramManager {
    val config: Config
    val gpuContext: GpuContext

    fun update(deltaSeconds: Float)
    fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines = Defines(), uniforms: Uniforms? = null): IComputeProgram<Uniforms>
    fun getComputeProgram(codeSourceAsset: Asset): IComputeProgram<Uniforms> = getComputeProgram(codeSourceAsset.toCodeSource(), Defines(), null)
    fun getComputeProgram(codeSourceAsset: Asset, defines: Defines = Defines(), uniforms: Uniforms? = null): IComputeProgram<Uniforms> = getComputeProgram(codeSourceAsset.toCodeSource(), defines, uniforms)
    fun getComputeProgram(codeSource: CodeSource): IComputeProgram<Uniforms> // TODO: Enhance and make like the other geometry pipeline

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                                 fragmentShaderSource: CodeSource?,
                                 geometryShaderSource: CodeSource?,
                                 defines: Defines,
                                 uniforms: T): IProgram<T>

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                                 fragmentShaderSource: CodeSource?,
                                 geometryShaderSource: CodeSource?,
                                 tesselationControlShaderSource: CodeSource?,
                                 tesselationEvaluationShaderSource: CodeSource?,
                                 defines: Defines,
                                 uniforms: T): IProgram<T>

    fun <T: Uniforms> getProgram(
        vertexShaderSource: CodeSource,
        fragmentShaderSource: CodeSource?,
        uniforms: T,
        defines: Defines
    ): IProgram<T> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, defines, uniforms)
    }

    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?): IProgram<Uniforms> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, Defines(), Uniforms.Empty)
    }

    val heightMappingFirstPassProgramDescription: ProgramDescription
    fun List<UniformDelegate<*>>.toUniformDeclaration(): String
    val Uniforms.shaderDeclarations: String
    fun CodeSource.toResultingShaderSource(defines: Defines): String
    // TODO: Make capable of animated uniforms stuff
    fun getFirstPassProgram(programDescription: ProgramDescription, uniforms: Uniforms): IProgram<Uniforms> = programDescription.run {
        getProgram(
            vertexShaderSource,
            fragmentShaderSource,
            geometryShaderSource,
            tesselationControlShaderSource,
            tesselationEvaluationShaderSource,
            defines ?: Defines(),
            uniforms,
        )
    }
}
