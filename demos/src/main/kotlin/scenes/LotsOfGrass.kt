package scenes

import Vector4fStruktImpl.Companion.sizeInBytes
import Vector4fStruktImpl.Companion.type
import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.annotations.All
import de.hanno.hpengine.Engine
import de.hanno.hpengine.SizeInBytes
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.artemis.forFirstEntityIfPresent
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.PrimitiveType
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.typed
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.SSBO
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.using
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.math.Vector4fStrukt
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.renderer.DrawElementsIndirectCommand
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.system.Extractor
import de.hanno.hpengine.toCount
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import org.apache.logging.log4j.LogManager
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.joml.AxisAngle4f
import org.joml.Vector3f
import org.joml.Vector3fc
import struktgen.api.get
import kotlin.random.Random

fun main() {
    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.LotsOfGrass, listOf(editorModule))

    val koin = getKoin(demoAndEngineConfig)
    val baseSystems = koin.getAll<BaseSystem>()
    val engine = Engine(
        baseSystems = baseSystems,
        config = koin.get<Config>(),
        input = koin.get<Input>(),
        window = koin.get<Window>(),
        addResourceContext = koin.get<AddResourceContext>()
    )

    addEditor(koin, engine)
    engine.runLotsOfGrass()
}

class Grass: Component()

@All(Grass::class, ModelComponent::class)
class GrassSystem(
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val renderStateContext: RenderStateContext,
    private val programManager: ProgramManager,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val materialSystem: MaterialSystem,
): BaseEntitySystem(), Extractor, DeferredRenderExtension, RenderSystem {
    private val logger = LogManager.getLogger(GrassSystem::class.java)
    init {
        logger.info("Creating system")
    }
    private val positions = renderStateContext.renderState.registerState {
        graphicsApi.PersistentShaderStorageBuffer(1000.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
    }

    private val uniforms1 = GrassDefaultUniforms()

    inner class GrassDefaultUniforms : StaticDefaultUniforms(graphicsApi) {
        var positions by SSBO(
            "vec4", 5, graphicsApi.PersistentShaderStorageBuffer(1000.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes)).typed(Vector4fStrukt.type)
        )
    }

    val program = programManager.getProgram(
        config.gameDir.resolve("shaders/first_pass_grass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        uniforms1
    )
    val programWithOutputChannel0 = programManager.getProgram(
        config.gameDir.resolve("shaders/first_pass_grass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("COLOR_OUTPUT_0", true)),
        uniforms1
    )

    private class Box(var value: Int)
    private val entityId = renderStateContext.renderState.registerState { Box(-1) }

    private val clusterPositions = buildList<Vector3f> {
        val clusterCount = 150
        repeat(clusterCount) {
            add(Vector3f(Random.nextFloat(), 0f, Random.nextFloat()).mul(3f*clusterCount))
        }
    }
    private val templatePositions: List<Vector3fc> = buildList {
        repeat(100) {
            val random0 = Random.nextFloat()
            val random1 = Random.nextFloat()
            val position = Vector3f((random0 - 0.5f) * 50, 0f, (random1 - 0.5f) * 50)
            clusterPositions.forEach {
                add(Vector3f(position).add(it))
            }
        }
    }
    override fun processSystem() { }

    override fun extract(currentWriteState: RenderState) {
        var anyGrassPresent = false
        forFirstEntityIfPresent {
            anyGrassPresent = true
        }
        if(anyGrassPresent) {
            val positionsToWrite = currentWriteState[positions]
            positionsToWrite.ensureCapacityInBytes(templatePositions.size.toCount() * SizeInBytes(Vector4fStrukt.sizeInBytes))

            var index = 0
            templatePositions.forEach {
                positionsToWrite.buffer.run {
                    positionsToWrite[index].set(it)
                }
                index++
            }
            forEachEntity {
                currentWriteState[entityId].value = it
            }
        }
    }

    override fun render(renderState: RenderState) {
        // Only render when not used as deferred renderer extension
        if(world.systems.firstIsInstanceOrNull<ExtensibleDeferredRenderer>() == null) {
            renderFirstPass(renderState)
        }
    }
    override fun renderFirstPass(renderState: RenderState) = graphicsApi.run {
        val entityId = renderState[entityId].value
        if(entityId == -1) return
        val modelCacheComponent = defaultBatchesSystem.modelCacheComponentMapper.get(entityId) ?: return
        val entityIndex = modelCacheComponent.gpuBufferIndex

        val materialComponent = defaultBatchesSystem.materialComponentMapper.get(entityId)
        val entitiesState = renderState[entitiesStateHolder.entitiesState]
        val camera = renderState[primaryCameraStateHolder.camera]

        val indexCount = modelCacheComponent.model.meshIndexCounts[0]
        val allocation = modelCacheComponent.allocation

        val program = if(world.systems.firstIsInstanceOrNull<ExtensibleDeferredRenderer>() == null) programWithOutputChannel0 else program
        program.use()
        using(program) { uniforms ->
            uniforms.apply {
                materials = renderState[materialSystem.materialBuffer]
                entities = renderState[entityBuffer.entitiesBuffer]
                positions = renderState[this@GrassSystem.positions]
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

                val vertexIndexBuffer = renderState[entitiesStateHolder.entitiesState].geometryBufferStatic

                depthMask = materialComponent.material.writesDepth
                cullFace = materialComponent.material.cullBackFaces
                depthTest = materialComponent.material.depthTest
                program.setTextureUniforms(graphicsApi, material = materialComponent.material)

                program.uniforms.entityIndex = entityIndex

                program.bind() // TODO: useAndBind seems not to work as this call is required, investigate
                vertexIndexBuffer.bind()
                vertexIndexBuffer.draw(
                    DrawElementsIndirectCommand(
                        count = indexCount,
                        instanceCount = templatePositions.size.toCount(),
                        firstIndex = allocation.indexOffset,
                        baseVertex = allocation.vertexOffset,
                        baseInstance = 0.toCount(),
                    ),
                    primitiveType = PrimitiveType.Triangles,
                    mode = if(config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill,
                    bindIndexBuffer = false,
                )
            }
        }
    }
}

fun Engine.runLotsOfGrass() {
    val textureManager = systems.firstIsInstance<TextureManagerBaseSystem>()
    world.loadScene {
        addPrimaryCameraControls()
        addStaticModelEntity("Plane", "assets/models/plane.obj", rotation = AxisAngle4f(1f, 0f, 0f, -180f)).apply {
            create(MaterialComponent::class.java).apply {
                material = Material(
                    "grass",
                    materialType = Material.MaterialType.FOLIAGE,
                    maps = mutableMapOf(
                        Material.MAP.DIFFUSE to textureManager.getStaticTextureHandle("assets/textures/grass.png", true, config.gameDir)
                    )
                )
            }
            create(Grass::class.java)
            create(PreventDefaultRendering::class.java)
        }
    }
    simulate()
}
