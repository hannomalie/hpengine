package de.hanno.hpengine.graphics.shader

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.ProgramChangeListenerManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class OpenGlProgramManager(
    private val graphicsApi: GraphicsApi,
    private val fileMonitor: FileMonitor,
    override val config: Config,
) : BaseSystem(), ProgramManager {

    private var programsCache: MutableList<Program<*>> = CopyOnWriteArrayList()

    private val programChangeListener = ProgramChangeListenerManager(fileMonitor)

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
            is Vec2 -> "uniform vec3 ${it.name};"
        }
    }
    override val Uniforms.shaderDeclarations get() = registeredUniforms.toUniformDeclaration()

    override val heightMappingFirstPassProgramDescription = getFirstPassHeightMappingProgramDescription()

    override fun <T: Uniforms> getComputeProgram(
        codeSource: FileBasedCodeSource,
        defines: Defines,
        uniforms: T
    ): ComputeProgramImpl<T> = ComputeProgramImpl(ComputeShader(graphicsApi, codeSource, defines), graphicsApi, fileMonitor, uniforms).apply {
        programsCache.add(this).apply {
            programChangeListener.run {
                reregisterListener { graphicsApi.run { reload() } }
            }
        }
        graphicsApi.run { load() }
    }

    override fun <T : Uniforms> getProgram(
        vertexShaderSource: CodeSource,
        fragmentShaderSource: CodeSource?,
        geometryShaderSource: CodeSource?,
        defines: Defines,
        uniforms: T
    ): ProgramImpl<T> = getProgram(
        vertexShaderSource, fragmentShaderSource, geometryShaderSource, null, null, defines, uniforms
    )

    override fun <T : Uniforms> getProgram(
        vertexShaderSource: CodeSource,
        fragmentShaderSource: CodeSource?,
        geometryShaderSource: CodeSource?,
        tesselationControlShaderSource: CodeSource?,
        tesselationEvaluationShaderSource: CodeSource?,
        defines: Defines,
        uniforms: T): ProgramImpl<T> = graphicsApi.run {
        ProgramImpl(
            vertexShader = VertexShader(graphicsApi, vertexShaderSource, defines),
            fragmentShader = fragmentShaderSource?.let { FragmentShader(graphicsApi, it, defines) },
            geometryShader = geometryShaderSource?.let { GeometryShader(graphicsApi, it, defines) },
            tesselationControlShader = tesselationControlShaderSource?.let { TesselationControlShader(graphicsApi, it, defines) },
            tesselationEvaluationShader = tesselationEvaluationShaderSource?.let { TesselationEvaluationShader(graphicsApi, it, defines) },
            uniforms = uniforms,
            defines = defines,
            graphicsApi = graphicsApi,
        ).apply {
            load()
            programsCache.add(this)
            programChangeListener.run {
            reregisterListener { reload() }
            }
        }
    }

    override fun getComputeProgram(codeSource: CodeSource): ComputeProgramImpl<Uniforms> = ComputeProgramImpl(
        ComputeShader(graphicsApi, codeSource, Defines()), graphicsApi, fileMonitor,  Uniforms.Empty,
    )

    override fun update(deltaSeconds: Float) { }

    override fun processSystem() {
        update(world.delta)
    }
}

private fun ProgramManager.getFirstPassHeightMappingProgramDescription() = ProgramDescription(
    vertexShaderSource = vertexShaderSource,
    tesselationControlShaderSource = tesselationControlShaderSource,
    tesselationEvaluationShaderSource = tesselationEvaluationShaderSource,
    geometryShaderSource = geometryShaderSource,
    fragmentShaderSource = fragmentShaderSource,
    defines = Defines(),
)


val ProgramManager.vertexShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_vertex.glsl"))
val ProgramManager.fragmentShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_fragment.glsl"))
val ProgramManager.tesselationControlShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_tesselation_control.glsl"))
val ProgramManager.tesselationEvaluationShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_tesselation_evaluation.glsl"))
val ProgramManager.geometryShaderSource
    get() = FileBasedCodeSource(config.engineDir.resolve("shaders/heightmapping_geometry.glsl"))