package scenes

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.*
import com.artemis.annotations.All
import com.artemis.utils.Bag
import de.hanno.hpengine.Engine
import de.hanno.hpengine.artemis.forFirstEntityIfPresent
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
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.SSBO
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import imgui.ImGui
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.joml.AxisAngle4f
import org.joml.Vector3f
import org.koin.core.annotation.Single
import struktgen.api.get
import kotlin.math.sin
import kotlin.random.Random

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig()

    val engine = createEngine(demoAndEngineConfig)

    engine.runCPUParticles()
}

private const val initialClusterCount = 150
private const val initialCountPerCluster = 100

class CPUParticles: Component() {
    var pointSize: Float = 5f
    var primitiveType = PrimitiveType.Triangles
    var clusterCount = initialClusterCount
    var countPerCluster = initialCountPerCluster

    var clusterPositions = calculateClusterPositions(initialClusterCount)
    var particlePositions: List<Vector3f> = calculateParticlePositions(initialClusterCount, clusterPositions)
}

@All(CPUParticles::class, ModelComponent::class)
@Single(binds = [Extractor::class, DeferredRenderExtension::class, BaseSystem::class])
class CPUParticleSystem(
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
    lateinit var particlesComponentMapper: ComponentMapper<CPUParticles>
    private val positions = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(1000).typed(Vector4fStrukt.type)
    }
    private val state = renderStateContext.renderState.registerState { CPUParticles() }

    private val uniforms1 = GPUParticlesFirstPassUniforms()

    inner class GPUParticlesFirstPassUniforms : StaticFirstPassUniforms(graphicsApi) {
        var positions by SSBO(
            "vec4", 5, graphicsApi.PersistentShaderStorageBuffer(1000).typed(Vector4fStrukt.type)
        )
    }

    val program = programManager.getProgram(
        config.gameDir.resolve("shaders/first_pass_particles_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        uniforms1
    )

    private class Box(var value: Int?)
    private val entityId = renderStateContext.renderState.registerState { Box(null) }

    override fun processSystem() { }

    override fun extract(currentWriteState: RenderState) {
        forFirstEntityIfPresent { entityId ->
            currentWriteState[this.entityId].value = entityId

            val particlePositions = particlesComponentMapper.get(entityId).particlePositions
            val positionsToWrite = currentWriteState[positions]
            positionsToWrite.ensureCapacityInBytes(particlePositions.size * Vector4fStrukt.sizeInBytes)

            particlePositions.forEachIndexed { index, it ->
                positionsToWrite.buffer.run {
                    positionsToWrite[index].set(it)
                }
            }
            val particles = particlesComponentMapper.get(entityId)

            currentWriteState[state].apply {
                primitiveType = particles.primitiveType
                this.particlePositions = particles.particlePositions
            }
        }
    }

    private var seconds = 0f
    override fun update(deltaSeconds: Float) {
        seconds += deltaSeconds

        forFirstEntityIfPresent {
            val particles = particlesComponentMapper.get(it)
            runBlocking(Dispatchers.Default) {
                particles.particlePositions.map {
                    launch {
                        it.y = sin(seconds * (it.x.toDouble() % 20)).toFloat()
                    }
                }.joinAll()
            }
        }
    }

    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        val entityId = renderState[entityId].value ?: return
        val modelCacheComponent = defaultBatchesSystem.modelCacheComponentMapper.get(entityId) ?: return
        val entityIndex = entityBuffer.getEntityIndex(entityId) ?: return

        val particlesComponent = particlesComponentMapper.get(entityId)
        val materialComponent = defaultBatchesSystem.materialComponentMapper.get(entityId)
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val camera = renderState[primaryCameraStateHolder.camera]

        val indexCount = modelCacheComponent.model.meshIndexCounts[0]
        val allocation = modelCacheComponent.allocation

        program.useAndBind(
            setUniforms = {
                materials = renderState[materialSystem.materialBuffer]
                entities = renderState[entityBuffer.entitiesBuffer]
                positions = renderState[this@CPUParticleSystem.positions]
                program.uniforms.indirect = false
                program.uniforms.vertices = entitiesState.vertexIndexBufferStatic.vertexStructArray
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
                setTextureUniforms(program, graphicsApi, materialComponent.material.maps)

                program.uniforms.entityIndex = entityIndex
            }
        ) {
            if(particlesComponent.primitiveType == PrimitiveType.Points) {
                graphicsApi.setPointsSize(particlesComponent.pointSize)
            }
            val vertexIndexBuffer = renderState[entitiesStateHolder.entitiesState].vertexIndexBufferStatic
            vertexIndexBuffer.indexBuffer.draw(
                DrawElementsIndirectCommand(
                    count = indexCount,
                    instanceCount = particlesComponent.particlePositions.size,
                    firstIndex = allocation.indexOffset,
                    baseVertex = allocation.vertexOffset,
                    baseInstance = 0,
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
        add(Vector3f(Random.nextFloat(), 0f, Random.nextFloat()).mul(3f * clusterCount))
    }
}

private fun calculateParticlePositions(instancesPerCluster: Int, clusterPositions: List<Vector3f>) = buildList {
    repeat(instancesPerCluster) {
        val random0 = Random.nextFloat()
        val random1 = Random.nextFloat()
        val position = Vector3f((random0 - 0.5f) * 50, 0f, (random1 - 0.5f) * 50)
        clusterPositions.forEach {
            add(Vector3f(position).add(it))
        }
    }
}

class CPUParticlesSelection(val component: CPUParticles): Selection
@Single(binds = [EntitySystem::class, EditorExtension::class])
class CPUParticlesEditorExtension: BaseSystem(), EditorExtension {
    override fun getSelectionForComponentOrNull(
        component: Component,
        entity: Int,
        components: Bag<Component>,
    ) = if(component is CPUParticles) {
        CPUParticlesSelection(component)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is CPUParticlesSelection) {
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
    } else {
        false
    }

    override fun processSystem() { }
}

fun Engine.runCPUParticles() {
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
            create(CPUParticles::class.java)
            create(PreventDefaultRendering::class.java)
        }
    }
    simulate()
}
