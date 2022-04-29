package de.hanno.hpengine.engine.graphics.shader

import com.artemis.BaseSystem
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.renderer.pipelines.FirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.GpuBuffer
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.model.material.ProgramDescription
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.ressources.CodeSource
import de.hanno.hpengine.util.ressources.FileBasedCodeSource
import de.hanno.hpengine.util.ressources.ReloadableCodeSource
import de.hanno.hpengine.util.ressources.StringBasedCodeSource
import de.hanno.hpengine.util.ressources.WrappedCodeSource
import de.hanno.hpengine.util.ressources.hasChanged
import org.joml.Vector3f
import org.lwjgl.BufferUtils
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
class SSBO(val dataType: String, val bindingIndex: Int, initial: GpuBuffer) : UniformDelegate<GpuBuffer>(initial)

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
                           override val config: Config) : BaseSystem(), ProgramManager<OpenGl> {

    override fun List<UniformDelegate<*>>.toUniformDeclaration() = joinToString("\n") {
        when (it) {
            is Mat4 -> "uniform mat4 ${it.name};"
            is Vec3 -> "uniform vec3 ${it.name};"
            is SSBO -> {
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
    override val heightMappingFirstPassProgram = getFirstPassHeightMappingProgram()
    override val heightMappingFirstPassProgramDescription = getFirstPassHeightMappingProgramDescription()

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

        return getProgram(vertexShaderSource, fragmentShaderSource, geometryShaderSource, null, null, defines, uniforms)
    }

    override fun <T : Uniforms> getProgram(vertexShaderSource: CodeSource,
                                           fragmentShaderSource: CodeSource?,
                                           geometryShaderSource: CodeSource?,
                                           tesselationControlShaderSource: CodeSource?,
                                           tesselationEvaluationShaderSource: CodeSource?,
                                           defines: Defines,
                                           uniforms: T): Program<T> {

        return gpuContext.invoke {
            Program(
                programManager = this,
                vertexShader = VertexShader(this, vertexShaderSource, defines),
                tesselationControlShader = tesselationControlShaderSource?.let { TesselationControlShader(this, it, defines) },
                tesselationEvaluationShader = tesselationEvaluationShaderSource?.let { TesselationEvaluationShader(this, it, defines) },
                geometryShader = geometryShaderSource?.let { GeometryShader(this, it, defines) },
                fragmentShader = fragmentShaderSource?.let { FragmentShader(this, it, defines) },
                defines = defines,
                uniforms = uniforms
            ).apply {
                programsCache.add(this)
                eventBus.register(this)
            }
        }
    }

    override fun getComputeProgram(codeSource: CodeSource) = gpuContext.invoke { ComputeProgram(this, ComputeShader(this, codeSource)) }

    var programsSourceCache: WeakHashMap<Shader, Int> = WeakHashMap()
    override fun update(deltaSeconds: Float) {
        if(config.debug.isUseFileReloading) {
            programsCache.forEach { program ->
                program.shaders.forEach { shader ->
                    if (shader.source is StringBasedCodeSource || shader.source is FileBasedCodeSource || shader.source is WrappedCodeSource || shader.source is ReloadableCodeSource) {
                        programsSourceCache.putIfAbsent(shader, shader.source.source.hashCode())
                        if (shader.source.hasChanged(programsSourceCache[shader]!!)) {
                            program.reload()
                            println("Reloaded ${program.name}")
                            programsSourceCache[shader] = shader.source.source.hashCode()
                        }
                    }
                }
            }
        }
    }

    override fun CodeSource.toResultingShaderSource(defines: Defines): String {
        return gpuContext.getOpenGlVersionsDefine() +
                gpuContext.getOpenGlExtensionsDefine() +
                defines.toString() +
                ShaderDefine.getGlobalDefinesString(config) +
                Shader.replaceIncludes(config.directories.engineDir, source, 0).left
    }

    override fun processSystem() {
        update(world.delta)
    }
}

private fun ProgramManager<*>.getFirstPassHeightMappingProgram(): Program<FirstPassUniforms> = getProgram(
    vertexShaderSource = vertexShaderSource,
    tesselationControlShaderSource = tesselationControlShaderSource,
    tesselationEvaluationShaderSource = tesselationEvaluationShaderSource,
    geometryShaderSource = geometryShaderSource,
    fragmentShaderSource = fragmentShaderSource,
    defines = Defines(),
    uniforms = StaticFirstPassUniforms(gpuContext)
)

private fun ProgramManager<*>.getFirstPassHeightMappingProgramDescription() = ProgramDescription(
    vertexShaderSource = vertexShaderSource,
    tesselationControlShaderSource = tesselationControlShaderSource,
    tesselationEvaluationShaderSource = tesselationEvaluationShaderSource,
    geometryShaderSource = geometryShaderSource,
    fragmentShaderSource = fragmentShaderSource,
    defines = Defines(),
)


val ProgramManager<*>.vertexShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_vertex.glsl"))
val ProgramManager<*>.fragmentShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_fragment.glsl"))
val ProgramManager<*>.tesselationControlShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_tesselation_control.glsl"))
val ProgramManager<*>.tesselationEvaluationShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_tesselation_evaluation.glsl"))
val ProgramManager<*>.geometryShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_geometry.glsl"))