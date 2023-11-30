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
import de.hanno.hpengine.graphics.buffer.GpuBuffer
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.*
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import imgui.ImGui
import org.joml.AxisAngle4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.core.annotation.Single
import struktgen.api.get
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
    var particleVelocities: List<Vector3f> = particlePositions.map { Vector3f(Random.nextFloat()) }
}

@All(GPUParticles::class, ModelComponent::class)
@Single(binds = [Extractor::class, DeferredRenderExtension::class, BaseSystem::class])
class GPUParticleSystem(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val modelSystem: ModelSystem,
    renderStateContext: RenderStateContext,
    programManager: ProgramManager,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
): BaseEntitySystem(), Extractor, DeferredRenderExtension {
    lateinit var particlesComponentMapper: ComponentMapper<GPUParticles>
    private val positions = graphicsApi.PersistentShaderStorageBuffer(1000).typed(Vector4fStrukt.type)
    private val velocities = graphicsApi.PersistentShaderStorageBuffer(1000).typed(Vector4fStrukt.type)

    inner class GPUParticlesFirstPassUniforms : StaticFirstPassUniforms(graphicsApi) {
        var positions by SSBO("vec4", 5, this@GPUParticleSystem.positions)
        var velocities by SSBO("vec4", 6, this@GPUParticleSystem.velocities)
    }

    val program = programManager.getProgram(
        config.gameDir.resolve("shaders/first_pass_particles_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        GPUParticlesFirstPassUniforms()
    )
    inner class GPUParticlesComputeUniforms: Uniforms() {
        var maxThreads by IntType(initialClusterCount * initialCountPerCluster)
        var positions by SSBO("vec4", 5, this@GPUParticleSystem.positions)
        var velocities by SSBO("vec4", 6, this@GPUParticleSystem.velocities)
        var time by FloatType(0f)
        var target by Vec2(Vector2f())
    }
    val computeProgram = programManager.getComputeProgram(
        config.gameDir.resolve("shaders/particles_compute.glsl").toCodeSource(),
        Defines(),
        GPUParticlesComputeUniforms()
    )

    private class Box(var value: Int?)
    private val entityId = renderStateContext.renderState.registerState { Box(null) }

    override fun processSystem() { }

    override fun inserted(entityId: Int) {
        particlesComponentMapper.get(entityId).apply {
            positions.ensureCapacityInBytes(particlePositions.size * Vector4fStrukt.sizeInBytes)
            velocities.ensureCapacityInBytes(particlePositions.size * Vector4fStrukt.sizeInBytes)

            particlePositions.forEachIndexed { index, it ->
                positions.buffer.run {
                    positions[index].set(it)
                }
                velocities.buffer.run {
                    velocities[index].set(Vector3f(Random.nextFloat()))
                }
            }
        }
    }
    override fun extract(currentWriteState: RenderState) {
        forFirstEntityIfPresent { entityId ->
            currentWriteState[this.entityId].value = entityId
        }
    }

    private var seconds = 0f
    override fun update(deltaSeconds: Float) {
        seconds += deltaSeconds
    }

    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        val entityId = renderState[entityId].value ?: return
        val modelCacheComponent = modelSystem.modelCacheComponentMapper.get(entityId) ?: return
        val entityIndex = modelSystem.entityIndices[entityId] ?: return // TODO: Encapsulate this in own system

        val particlesComponent = particlesComponentMapper.get(entityId)

        computeProgram.useAndBind(
            setUniforms = {
                maxThreads = particlesComponent.particlePositions.size
                time = seconds
                target = Vector2f(cos(time%1000), sin(time%1000)).mul(100f)
            },
        ) {
            computeProgram.dispatchCompute(max(1, particlesComponent.particlePositions.size/8), 1, 1)
//            graphicsApi.finish()
        }

        val materialComponent = modelSystem.materialComponentMapper.get(entityId)
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val camera = renderState[primaryCameraStateHolder.camera]

        val indexCount = modelCacheComponent.model.meshIndexCounts[0]
        val allocation = modelCacheComponent.allocation

        program.useAndBind(
            setUniforms = {
                materials = entitiesState.materialBuffer
                entities = entitiesState.entitiesBuffer
//                positions = this@GPUParticleSystem.positions
//                velocities = this@GPUParticleSystem.velocities
                program.uniforms.indirect = false
                program.uniforms.vertices = entitiesState.vertexIndexBufferStatic.vertexStructArray
                viewMatrix = camera.viewMatrixAsBuffer
                lastViewMatrix = camera.viewMatrixAsBuffer
                projectionMatrix = camera.projectionMatrixAsBuffer
                viewProjectionMatrix = camera.viewProjectionMatrixAsBuffer

                eyePosition = camera.getPosition()
                near = camera.near
                far = camera.far
                time = renderState.time.toInt()

                program.uniforms.entityBaseIndex = 0
                program.uniforms.indirect = false


                depthMask = materialComponent.material.writesDepth
                cullFace = materialComponent.material.cullBackFaces
                depthTest = materialComponent.material.depthTest
                program.setTextureUniforms(graphicsApi, materialComponent.material.maps)

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

class GPUParticlesSelection(val component: GPUParticles): Selection
@Single(binds = [EntitySystem::class, EditorExtension::class])
class GPUParticlesEditorExtension: BaseSystem(), EditorExtension {
    override fun getSelectionForComponentOrNull(
        component: Component,
        entity: Int,
        components: Bag<Component>,
    ) = if(component is GPUParticles) {
        GPUParticlesSelection(component)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is GPUParticlesSelection) {
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
    }
    simulate()
}
