package de.hanno.hpengine.graphics.shader

import com.artemis.BaseSystem
import de.hanno.hpengine.backend.OpenGl
import de.hanno.hpengine.bus.EventBus
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.shader.api.Shader
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class OpenGlProgramManager(override val gpuContext: OpenGLContext,
                           private val eventBus: EventBus,
                           override val config: Config
) : BaseSystem(), ProgramManager<OpenGl> {

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

    var programsCache: MutableList<IProgram<*>> = CopyOnWriteArrayList()

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
                    if (shader.source is StringBasedCodeSource || shader.source is FileBasedCodeSource || shader.source is WrappedCodeSource) {
                        programsSourceCache.putIfAbsent(shader, shader.source.source.hashCode())
                        if (shader.source.hasChanged(programsSourceCache[shader]!!)) {
                            // TODO: Find a better way fot this check
                            if(program is Reloadable) {
                                program.reload()
                                println("Reloaded ${program.name}")
                                programsSourceCache[shader] = shader.source.source.hashCode()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun CodeSource.toResultingShaderSource(defines: Defines): String {
        return gpuContext.getOpenGlVersionsDefine() +
                gpuContext.getOpenGlExtensionsDefine() +
                defines.joinToString { it.defineString + "\n" } +
                ShaderDefine.getGlobalDefinesString(config) +
                de.hanno.hpengine.graphics.shader.Shader.replaceIncludes(config.directories.engineDir, source, 0).first
    }

    override fun processSystem() {
        update(world.delta)
    }
}

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