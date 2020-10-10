package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.directory.Asset
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.HpVector4f
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
                   uniforms: T): Program<T>

    fun <T: Uniforms> getProgram(vertexShaderSource: CodeSource,
                                 fragmentShaderSource: CodeSource?,
                                 uniforms: T): Program<T> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, Defines(), uniforms)
    }

    fun getProgram(vertexShaderSource: CodeSource,
                 fragmentShaderSource: CodeSource?): Program<Uniforms> {

        return getProgram(vertexShaderSource, fragmentShaderSource, null, Defines(), Uniforms.Empty)
    }

    val linesProgram: Program<LinesProgramUniforms>
    fun List<UniformDelegate<*>>.toUniformDeclaration(): String
    val Uniforms.shaderDeclarations: String
}

class LinesProgramUniforms(gpuContext: GpuContext<*>) : Uniforms() {
    val vertices by SSBO("vec4", 7, PersistentMappedStructBuffer(100, gpuContext, { HpVector4f() }))
    val modelMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val viewMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val projectionMatrix by Mat4(BufferUtils.createFloatBuffer(16).apply { Transform().get(this) })
    val color by Vec3(org.joml.Vector3f(1f,0f,0f))
}