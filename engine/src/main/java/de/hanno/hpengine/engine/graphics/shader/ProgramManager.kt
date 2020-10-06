package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.Asset
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.lwjgl.BufferUtils

interface ProgramManager<BACKEND: BackendType> : Manager {
    val gpuContext: GpuContext<BACKEND>

    fun update(deltaSeconds: Float)
    fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource, defines: Defines = Defines()): Int

    @JvmDefault
    fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource) = loadShader(shaderType, shaderSource, Defines())


    fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines = Defines(), uniforms: Uniforms? = null): ComputeProgram
    fun getComputeProgram(codeSourceAsset: Asset): ComputeProgram = getComputeProgram(codeSourceAsset.toCodeSource(), Defines(), null)
    fun getComputeProgram(codeSourceAsset: Asset, defines: Defines = Defines(), uniforms: Uniforms? = null): ComputeProgram = getComputeProgram(codeSourceAsset.toCodeSource(), defines, uniforms)

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?,
                   geometryShaderSource: CodeSource?,
                   defines: Defines,
                   uniforms: T?): Program<T>

    fun getProgram(vertexShaderSource: CodeSource,
                   fragmentShaderSource: CodeSource?): Program<Uniforms> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, Defines(), null)
    }

    fun getProgram(vertexShaderAsset: Asset,
                   fragmentShaderAsset: Asset?): Program<Uniforms> {

        return getProgram(vertexShaderAsset, fragmentShaderAsset, null, Defines(), null)
   }

    fun getProgram(vertexShaderAsset: Asset,
                   fragmentShaderAsset: Asset? = null,
                   geometryShaderAsset: Asset? = null,
                   defines: Defines = Defines(),
                   uniforms: Uniforms?): Program<Uniforms> = getProgram(

            vertexShaderAsset.toCodeSource(),
            fragmentShaderAsset?.toCodeSource(),
            geometryShaderAsset?.toCodeSource(),
            defines,
            uniforms
    )

    val linesProgram: Program<LinesProgramUniforms>
    fun UniformDelegate<*>.dataTypeAndName(): String
    fun List<UniformDelegate<*>>.toUniformDeclaration(): String
    val Uniforms.shaderDeclarations: String
    fun Program<*>.bind(uniforms: Uniforms)
}

class LinesProgramUniforms: Uniforms() {
    val modelMatrix by Mat4("modelMatrix", BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val viewMatrix by Mat4("viewMatrix", BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4("projectionMatrix", BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val color by Vec3("color", org.joml.Vector3f(1f,0f,0f))
}