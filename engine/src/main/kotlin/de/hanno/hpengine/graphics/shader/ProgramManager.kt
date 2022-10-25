package de.hanno.hpengine.graphics.shader

import Vector4fStruktImpl.Companion.sizeInBytes
import de.hanno.hpengine.backend.BackendType
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.directory.Asset
import de.hanno.hpengine.graphics.GpuContext
import de.hanno.hpengine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.PersistentMappedBuffer
import de.hanno.hpengine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.Transform
import de.hanno.hpengine.ressources.CodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.Vector3f
import org.lwjgl.BufferUtils

interface ProgramManager<BACKEND: BackendType> {
    val config: Config
    val gpuContext: GpuContext<BACKEND>

    fun update(deltaSeconds: Float)
    fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines = Defines(), uniforms: Uniforms? = null): ComputeProgram
    fun getComputeProgram(codeSourceAsset: Asset): ComputeProgram = getComputeProgram(codeSourceAsset.toCodeSource(), Defines(), null)
    fun getComputeProgram(codeSourceAsset: Asset, defines: Defines = Defines(), uniforms: Uniforms? = null): ComputeProgram = getComputeProgram(codeSourceAsset.toCodeSource(), defines, uniforms)
    fun getComputeProgram(codeSource: CodeSource): ComputeProgram // TODO: Enhance and make like the other geometry pipeline

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                                 fragmentShaderSource: CodeSource?,
                                 geometryShaderSource: CodeSource?,
                                 defines: Defines,
                                 uniforms: T): Program<T>

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                                 fragmentShaderSource: CodeSource?,
                                 geometryShaderSource: CodeSource?,
                                 tesselationControlShaderSource: CodeSource?,
                                 tesselationEvaluationShaderSource: CodeSource?,
                                 defines: Defines,
                                 uniforms: T): Program<T>

    fun <T: Uniforms> getProgram(
        vertexShaderSource: CodeSource,
        fragmentShaderSource: CodeSource?,
        uniforms: T,
        defines: Defines
    ): Program<T> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, defines, uniforms)
    }

    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?): Program<Uniforms> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, Defines(), Uniforms.Empty)
    }

    val linesProgram: Program<LinesProgramUniforms>
    val heightMappingFirstPassProgram: Program<FirstPassUniforms>
    val heightMappingFirstPassProgramDescription: ProgramDescription
    fun List<UniformDelegate<*>>.toUniformDeclaration(): String
    val Uniforms.shaderDeclarations: String
    fun CodeSource.toResultingShaderSource(defines: Defines): String
    // TODO: Make capable of animated uniforms stuff
    fun getFirstPassProgram(programDescription: ProgramDescription): Program<Uniforms> = programDescription.run {
        getProgram(
            vertexShaderSource,
            fragmentShaderSource,
            geometryShaderSource,
            tesselationControlShaderSource,
            tesselationEvaluationShaderSource,
            defines ?: Defines(),
            StaticFirstPassUniforms(gpuContext),
        )
    }
}

class LinesProgramUniforms(gpuContext: GpuContext<*>) : Uniforms() {
    var vertices by SSBO("vec4", 7, PersistentMappedBuffer(100 * Vector4fStrukt.sizeInBytes, gpuContext))
    val modelMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val color by Vec3(Vector3f(1f,0f,0f))
}