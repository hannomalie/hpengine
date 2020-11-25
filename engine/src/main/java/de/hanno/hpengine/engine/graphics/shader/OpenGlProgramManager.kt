package de.hanno.hpengine.engine.graphics.shader

import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.StringBasedCodeSource
import de.hanno.hpengine.util.ressources.WrappedCodeSource
import de.hanno.hpengine.util.ressources.hasChanged
import de.hanno.struct.Struct
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.io.IOException
import java.nio.FloatBuffer
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class UniformDelegate<T>(var _value: T) : ReadWriteProperty<Uniforms, T> {
    lateinit var name: String
        internal set
    override fun getValue(thisRef: Uniforms, property: KProperty<*>): T = _value
    override fun setValue(thisRef: Uniforms, property: KProperty<*>, value: T) {
        _value = value
    }

}

class IntType(initial: Int = 0): UniformDelegate<Int>(initial)
class FloatType(initial: Float = 0f): UniformDelegate<Float>(initial)
class BooleanType(initial: Boolean): UniformDelegate<Boolean>(initial)
class Mat4(initial: FloatBuffer = BufferUtils.createFloatBuffer(16).apply { Transform().get(this) }) : UniformDelegate<FloatBuffer>(initial)
class Vec3(initial: Vector3f) : UniformDelegate<Vector3f>(initial)
class SSBO<T: Struct>(val dataType: String, val bindingIndex: Int, initial: PersistentMappedStructBuffer<T>) : UniformDelegate<PersistentMappedStructBuffer<T>>(initial)

open class Uniforms {
    val registeredUniforms = mutableListOf<UniformDelegate<*>>()

    operator fun <T> UniformDelegate<T>.provideDelegate(thisRef: Uniforms, prop: KProperty<*>): ReadWriteProperty<Uniforms, T> {
        return this.apply {
            this.name = prop.name
            thisRef.registeredUniforms.add(this)
        }
    }
    companion object {
        val Empty = Uniforms()
    }
}

class OpenGlProgramManager(override val gpuContext: OpenGLContext,
                           private val eventBus: EventBus,
                           val config: Config) : ProgramManager<OpenGl> {

    override fun List<UniformDelegate<*>>.toUniformDeclaration() = joinToString("\n") {
        when (it) {
            is Mat4 -> "uniform mat4 ${it.name};"
            is Vec3 -> "uniform vec3 ${it.name};"
            is SSBO<*> -> {
                """layout(std430, binding=${it.bindingIndex}) buffer _${it.name} {
                      ${it.dataType} ${it.name}[];
                   };""".trimIndent()
            }
            is IntType -> "uniform int ${it.name};"
            is BooleanType -> "uniform bool ${it.name};"
            is FloatType -> "uniform float ${it.name};"
        }
    }
    override val Uniforms.shaderDeclarations
        get() = registeredUniforms.toUniformDeclaration()

    var programsCache: MutableList<AbstractProgram<*>> = CopyOnWriteArrayList()

    override val linesProgram = run {
        val uniforms = LinesProgramUniforms(gpuContext)
        getProgram(
                StringBasedCodeSource("mvp_vertex_vec4", """
                //include(globals_structs.glsl)
                
                ${uniforms.shaderDeclarations}

                in vec4 in_Position;

                out vec4 pass_Position;
                out vec4 pass_WorldPosition;

                void main()
                {
                	vec4 vertex = vertices[gl_VertexID];
                	vertex.w = 1;

                	pass_WorldPosition = ${uniforms::modelMatrix.name} * vertex;
                	pass_Position = ${uniforms::projectionMatrix.name} * ${uniforms::viewMatrix.name} * pass_WorldPosition;
                    gl_Position = pass_Position;
                }
            """.trimIndent()),
                StringBasedCodeSource("simple_color_vec3", """
            ${uniforms.shaderDeclarations}

            layout(location=0)out vec4 out_color;

            void main()
            {
                out_color = vec4(${uniforms::color.name},1);
            }
        """.trimIndent()), null, Defines(), uniforms
        )
    }

    override fun getComputeProgram(codeSource: FileBasedCodeSource, defines: Defines, uniforms: Uniforms?): ComputeProgram {
        return gpuContext.invoke {
            val program = ComputeProgram(this, codeSource, defines)
            programsCache.add(program)
            eventBus.register(program)
            program
        }
    }

    override fun <T : Uniforms> getProgram(vertexShaderSource: CodeSource,
                                           fragmentShaderSource: CodeSource?,
                                           geometryShaderSource: CodeSource?,
                                           defines: Defines,
                                           uniforms: T): Program<T> {

        return gpuContext.invoke {
            Program(this, vertexShaderSource, geometryShaderSource, fragmentShaderSource, defines, uniforms).apply {
                programsCache.add(this)
                eventBus.register(this)
            }
        }
    }

    var programsSourceCache: WeakHashMap<Shader, String> = WeakHashMap()
    override fun update(deltaSeconds: Float) {
        if(config.debug.isUseFileReloading) {
            programsCache.forEach { program ->
                program.shaders.forEach { shader ->
                    if (shader.shaderSource is StringBasedCodeSource || shader.shaderSource is WrappedCodeSource) {
                        programsSourceCache.putIfAbsent(shader, shader.shaderSource.source)
                        if (shader.shaderSource.hasChanged(programsSourceCache[shader]!!)) program.reload()
                    }
                }
            }
        }
    }

    override fun loadShader(shaderType: Shader.ShaderType, shaderSource: CodeSource, defines: Defines): Int {

        var resultingShaderSource = (gpuContext.getOpenGlVersionsDefine()
                + gpuContext.getOpenGlExtensionsDefine()
                + defines.toString()
                + ShaderDefine.getGlobalDefinesString(config))

        var newlineCount = resultingShaderSource.split("\n".toRegex()).toTypedArray().size - 1

        var actualShaderSource = shaderSource.source

        try {
            val tuple = Shader.replaceIncludes(config.directories.engineDir, actualShaderSource, newlineCount)
            actualShaderSource = tuple.left
            newlineCount = tuple.right
            resultingShaderSource += actualShaderSource
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val shaderId: Int = gpuContext.invoke {
            GL20.glCreateShader(shaderType.glShaderType).also { shaderId ->
                GL20.glShaderSource(shaderId, resultingShaderSource)
                GL20.glCompileShader(shaderId)
            }
        }

        val shaderLoadFailed = gpuContext.invoke {
            val shaderStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS)
            if (shaderStatus == GL11.GL_FALSE) {
                System.err.println("Could not compile " + shaderType + ": " + shaderSource.name)
                var shaderInfoLog = GL20.glGetShaderInfoLog(shaderId, 10000)
                shaderInfoLog = Shader.replaceLineNumbersWithDynamicLinesAdded(shaderInfoLog, newlineCount)
                System.err.println(shaderInfoLog)
                true
            } else false
        }

        if (shaderLoadFailed) {
            throw Shader.ShaderLoadException(resultingShaderSource)
        }

        Shader.LOGGER.finer(resultingShaderSource)
        gpuContext.exceptionOnError("loadShader: " + shaderType + ": " + shaderSource.name)

        return shaderId
    }
}
