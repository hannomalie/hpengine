package scenes

import AttractorStruktImpl.Companion.sizeInBytes
import AttractorStruktImpl.Companion.type
import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.annotations.One
import com.artemis.utils.Bag
import de.hanno.hpengine.Engine
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.getOrNull
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.engine.graphics.imgui.floatInput
import de.hanno.hpengine.engine.graphics.imgui.intInput
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.Vector3fStrukt
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.toCount
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import imgui.ImGui
import org.apache.logging.log4j.LogManager
import org.joml.AxisAngle4f
import org.joml.Vector3f
import org.koin.core.annotation.Single
import struktgen.api.Strukt
import struktgen.api.forIndex
import struktgen.api.get
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runGPUParticles()
}

private const val initialClusterCount = 150
private const val initialCountPerCluster = 1000

class GPUParticles: Component() {
    var pointSize: Float = 5f
    var primitiveType = PrimitiveType.Triangles
    var clusterCount = initialClusterCount
    var countPerCluster = initialCountPerCluster

    var clusterPositions = calculateClusterPositions(initialClusterCount)
    var particlePositions: List<Vector3f> = calculateParticlePositions(initialClusterCount, clusterPositions)
}
class Attractor: Component() {
    var radius = 100f
    var strength = 1f
}

interface AttractorStrukt: Strukt {
    context(ByteBuffer) val position: Vector3fStrukt
    context(ByteBuffer) var radius: Float

    context(ByteBuffer) var strength: Float
    context(ByteBuffer) var padding0: Float
    context(ByteBuffer) var padding1: Float
    context(ByteBuffer) var padding2: Float

    companion object
}

@All(ModelComponent::class)
@One(GPUParticles::class, Attractor::class)
//@Single(binds = [Extractor::class, DeferredRenderExtension::class, BaseSystem::class])
class GPUParticleSystem(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    renderStateContext: RenderStateContext,
    programManager: ProgramManager,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val materialSystem: MaterialSystem,
): BaseEntitySystem(), Extractor, DeferredRenderExtension {
    private val logger = LogManager.getLogger(GPUParticleSystem::class.java)
    init {
        logger.info("Creating system")
    }
    lateinit var attractorComponentMapper: ComponentMapper<Attractor>
    lateinit var particlesComponentMapper: ComponentMapper<GPUParticles>
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>

    private val positions = graphicsApi.PersistentShaderStorageBuffer(1000.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    private val velocities = graphicsApi.PersistentShaderStorageBuffer(1000.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    private val attractors = graphicsApi.PersistentShaderStorageBuffer(1000.toCount() * SizeInBytes(AttractorStrukt.sizeInBytes)).typed(AttractorStrukt.type)

    inner class GPUParticlesDefaultUniforms : StaticDefaultUniforms(graphicsApi) {
        var positions by SSBO("vec4", 5, this@GPUParticleSystem.positions)
        var velocities by SSBO("vec4", 8, this@GPUParticleSystem.velocities)
    }

    val program = programManager.getProgram(
        config.gameDir.resolve("shaders/first_pass_particles_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        GPUParticlesDefaultUniforms()
    )
    inner class GPUParticlesComputeUniforms: Uniforms() {
        var maxThreads by IntType(initialClusterCount * initialCountPerCluster)
        var positions by SSBO("vec4", 5, this@GPUParticleSystem.positions)
        var velocities by SSBO("vec4", 6, this@GPUParticleSystem.velocities)
        var attractors by SSBO("vec4", 7, this@GPUParticleSystem.attractors)
        var time by FloatType(0f)
        var targetCount by IntType()
    }
    val computeProgram = programManager.getComputeProgram(
        config.gameDir.resolve("shaders/particles_compute.glsl").toCodeSource(),
        Defines(),
        GPUParticlesComputeUniforms()
    )

    private class Box(var value: Int?)
    private val entityId = renderStateContext.renderState.registerState { Box(null) }
    private val targetCount = renderStateContext.renderState.registerState { Box(0) }

    override fun processSystem() {
        var attractorCounter = 0
        forEachEntity { entityId ->
            attractorComponentMapper.getOrNull(entityId)?.apply {
                transformComponentMapper.getOrNull(entityId)?.apply {
                    transform.translation(
                        Vector3f(
                        cos(seconds % 1000) * (50 + attractorCounter * 50),
                        if(attractorCounter%2 == 0) sin(seconds % 1000) * (50 + attractorCounter * 50) else cos(seconds % 1000) * (50 + attractorCounter * 50),
                        if(attractorCounter%3 == 0) sin(seconds % 1000) * (50 + attractorCounter * 50) else cos(seconds % 1000) * (50 + attractorCounter * 50),
                        )
                    )
                }
                attractorCounter++
            }
        }
    }

    override fun inserted(entityId: Int) {
        particlesComponentMapper.getOrNull(entityId)?.apply {
            positions.ensureCapacityInBytes(particlePositions.size.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes))
            velocities.ensureCapacityInBytes(particlePositions.size.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes))

            particlePositions.forEachIndexed { index, it ->
                positions.buffer.run {
                    positions[index].set(it)
                }
                velocities.buffer.run {
                    velocities[index].set(Vector3f())
                }
            }
        }
    }
    override fun extract(currentWriteState: RenderState) {
        var attractorCounter = 0
        forEachEntity { entityId ->
            particlesComponentMapper.getOrNull(entityId)?.apply {// TODO: Extract all entity ids
                currentWriteState[this@GPUParticleSystem.entityId].value = entityId
            }
            attractorComponentMapper.getOrNull(entityId)?.apply {
                transformComponentMapper.get(entityId).transform.apply {
                    attractors.run {
                        forIndex(attractorCounter) {
                            it.position.set(position)
                            it.radius = radius
                            it.strength = strength
                        }
                    }
                }
                attractorCounter++
            }
        }
        currentWriteState[targetCount].value = attractorCounter
    }

    private var seconds = 0f
    override fun update(deltaSeconds: Float) {
        seconds += deltaSeconds
    }

    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        val entityId = renderState[entityId].value ?: return
        val modelCacheComponent = defaultBatchesSystem.modelCacheComponentMapper.get(entityId) ?: return
        val entityIndex = entityBuffer.getEntityIndex(entityId) ?: return // TODO: Encapsulate this in own system

        val particlesComponent = particlesComponentMapper.get(entityId)

        computeProgram.useAndBind(
            setUniforms = {
                maxThreads = particlesComponent.particlePositions.size
                time = seconds
                targetCount = renderState[this@GPUParticleSystem.targetCount].value ?: 0
            },
        ) {
            computeProgram.dispatchCompute(max(1, particlesComponent.particlePositions.size/8).toCount(), 1.toCount(), 1.toCount())
        }

        val materialComponent = defaultBatchesSystem.materialComponentMapper.get(entityId)
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val camera = renderState[primaryCameraStateHolder.camera]

        val indexCount = modelCacheComponent.model.meshIndexCounts[0]
        val allocation = modelCacheComponent.allocation

        program.useAndBind(
            setUniforms = {
                materials = renderState[materialSystem.materialBuffer]
                entities = renderState[entityBuffer.entitiesBuffer]
                program.uniforms.indirect = false
                program.uniforms.vertices = entitiesState.geometryBufferStatic.vertexStructArray
                viewMatrix = camera.viewMatrixBuffer
                lastViewMatrix = camera.viewMatrixBuffer
                projectionMatrix = camera.projectionMatrixBuffer
                viewProjectionMatrix = camera.viewProjectionMatrixBuffer

                eyePosition = camera.getPosition()
                near = camera.near
                far = camera.far
                time = renderState.time.toInt()

                program.uniforms.entityBaseIndex = 0
                program.uniforms.indirect = false


                depthMask = materialComponent.material.writesDepth
                cullFace = materialComponent.material.cullBackFaces
                depthTest = materialComponent.material.depthTest
                program.setTextureUniforms(graphicsApi, material = materialComponent.material)

                program.uniforms.entityIndex = entityIndex
            }
        ) {
            if(particlesComponent.primitiveType == PrimitiveType.Points) {
                graphicsApi.setPointsSize(particlesComponent.pointSize)
            }
            val vertexIndexBuffer = renderState[entitiesStateHolder.entitiesState].geometryBufferStatic
            vertexIndexBuffer.draw(
                DrawElementsIndirectCommand(
                    count = indexCount,
                    instanceCount = particlesComponent.particlePositions.size.toCount(),
                    firstIndex = allocation.indexOffset,
                    baseVertex = allocation.vertexOffset,
                    baseInstance = 0.toCount(),
                ),
                primitiveType = particlesComponent.primitiveType,
                mode = if (config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill,
                bindIndexBuffer = true,
            )
        }
    }
}

private fun calculateClusterPositions(clusterCount: Int) = buildList<Vector3f> {
    repeat(clusterCount) {
        add(Vector3f(Random.nextFloat()-0.5f, Random.nextFloat()-0.5f, Random.nextFloat()-0.5f).mul(3f * clusterCount))
    }
}

private fun calculateParticlePositions(instancesPerCluster: Int, clusterPositions: List<Vector3f>) = buildList {
    repeat(instancesPerCluster) {
        val random0 = Random.nextFloat()
        val random1 = Random.nextFloat()
        val random2 = Random.nextFloat()
        val position = Vector3f((random0 - 0.5f), (random1 - 0.5f), (random2 - 0.5f)).mul(100f)
        clusterPositions.forEach {
            add(Vector3f(position).add(it))
        }
    }
}

class GPUParticlesSelection(val component: GPUParticles): Selection
class AttractorSelection(val component: Attractor): Selection

@Single(binds = [EntitySystem::class, EditorExtension::class])
class GPUParticlesEditorExtension: BaseSystem(), EditorExtension {
    override fun getSelectionForComponentOrNull(
        component: Component,
        entity: Int,
        components: Bag<Component>,
    ) = when (component) {
        is GPUParticles -> GPUParticlesSelection(component)
        is Attractor -> AttractorSelection(component)
        else -> null
    }

    override fun Window.renderRightPanel(selection: Selection?) = when (selection) {
        is GPUParticlesSelection -> {
            intInput("Cluster count", selection.component.clusterCount, 1, 10000) {
                selection.component.clusterCount = it[0]
                selection.component.clusterPositions = calculateClusterPositions(selection.component.clusterCount)
                selection.component.particlePositions = calculateParticlePositions(selection.component.countPerCluster, selection.component.clusterPositions)
            }
            intInput("Instances per cluster", selection.component.countPerCluster, 1, 10000) {
                selection.component.countPerCluster = it[0]
                selection.component.clusterPositions = calculateClusterPositions(selection.component.clusterCount)
                selection.component.particlePositions = calculateParticlePositions(selection.component.countPerCluster, selection.component.clusterPositions)
            }
            if (ImGui.beginCombo("Primitive Type", selection.component.primitiveType.toString())) {
                PrimitiveType.entries.forEach {
                    val selected = selection.component.primitiveType == it
                    if (ImGui.selectable(it.toString(), selected)) {
                        selection.component.primitiveType = it
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
            if(selection.component.primitiveType == PrimitiveType.Points) {
                floatInput("Point size", selection.component.pointSize, 0.1f, 50f) {
                    selection.component.pointSize = it[0]
                }
            }
            true
        }
        is AttractorSelection -> {
            floatInput("Strength", selection.component.strength, 0f, 50f) {
                selection.component.strength = it[0]
            }
            floatInput("Radius", selection.component.radius, 1f, 200f) {
                selection.component.radius = it[0]
            }
            true
        }
        else -> false
    }

    override fun processSystem() { }
}

fun Engine.runGPUParticles() {
    world.loadScene {
        addStaticModelEntity(
            "Particle",
            "assets/models/plane.obj",
            rotation = AxisAngle4f(1f, 0f, 0f, -180f),
            scale = Vector3f(0.1f)
        ).apply {
            create(MaterialComponent::class.java).apply {
                material = Material(
                    "particles",
                    materialType = Material.MaterialType.FOLIAGE,
                )
            }
            create(GPUParticles::class.java)
            create(PreventDefaultRendering::class.java)
        }
        val material = Material(
            "attractor",
            diffuse = Vector3f(1f, 0f, 0f),
            materialType = Material.MaterialType.UNLIT,
            cullBackFaces = false
        )
        repeat(20) {
            addStaticModelEntity(
                "Target$it",
                "assets/models/sphere.obj",
                rotation = AxisAngle4f(1f, 0f, 0f, -180f),
                scale = Vector3f(5f)
            ).apply {
                create(MaterialComponent::class.java).apply {
                    this.material = material
                }
                create(TransformComponent::class.java)
                create(Attractor::class.java).apply {
                    radius = 100f
                    strength = 1f
                }
            }
        }
    }
    simulate()
}
