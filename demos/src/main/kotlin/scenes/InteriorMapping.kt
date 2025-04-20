package scenes

import com.artemis.BaseEntitySystem
import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.annotations.All
import de.hanno.hpengine.Engine
import de.hanno.hpengine.artemis.mapEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.*
import de.hanno.hpengine.graphics.editor.editorModule
import de.hanno.hpengine.graphics.renderer.forward.StaticDefaultUniforms
import de.hanno.hpengine.graphics.renderer.pipelines.setCommonUniformValues
import de.hanno.hpengine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.shader.useAndBind
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.texture.StaticHandleImpl
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.UploadState
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.WorldSpaceTexCoords
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.visibility.InvisibleComponent
import de.hanno.hpengine.world.addStaticModelEntity
import de.hanno.hpengine.world.loadScene
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.joml.AxisAngle4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.koin.dsl.binds
import org.koin.dsl.module

fun main() {

    val demoAndEngineConfig = createDemoAndEngineConfig(Demo.InteriorMapping, listOf(
        editorModule,
        module {
            single {
                InteriorMappingRenderSystem(get(), get(), get(), get(), get(), get(), get(), get())
            } binds arrayOf(RenderSystem::class, BaseSystem::class, InteriorMappingRenderSystem::class)
        }
    ))

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
    engine.runInteriorMapping()
}

@All(InteriorMappingComponent::class)
class InteriorMappingRenderSystem(
    private val programManager: ProgramManager,
    private val config: Config,
    private val graphicsApi: GraphicsApi,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val materialSystem: MaterialSystem,
    private val entityBuffer: EntityBuffer,
): RenderSystem, BaseEntitySystem() {

    private val program = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/color_only/color_out_fragment.glsl")
            .toCodeSource()
            .enhanced("interior_fragment", arrayOf(
                "//END" to """
                    vec3 boxProjected = boxProject(eyePosition, V, entity.min, entity.max);
                    mat4 rotationMatrix = rotationMatrix(vec3(0,1,0), 1.57 * (entityId % 10));
                    vec3 newV = (rotationMatrix * vec4(boxProjected, 0)).xyz;
                    out_color.rgba = texture(environmentMap, newV);
                    """.trimIndent()
            )),
        null,
        Defines(),
        StaticDefaultUniforms(graphicsApi)
    )
    private val entityIds = mutableListOf<Int>()
    override fun processSystem() {
        entityIds.clear()
        entityIds.addAll(mapEntity { it }) // move to extract
    }
    override fun render(renderState: RenderState) {

        val batches = renderState[defaultBatchesSystem.renderBatchesStatic].filter {
            it.entityId in entityIds
        }

        val geometryBuffer = renderState[entitiesStateHolder.entitiesState].geometryBufferStatic

        batches.forEach { batch ->
            graphicsApi.run {

                cullFace = batch.material.cullingEnabled
                cullMode = if(batch.material.cullFrontFaces) CullMode.FRONT else CullMode.BACK
                depthTest = batch.material.depthTest
                depthMask = batch.material.writesDepth

                program.useAndBind(setUniforms = {
                    setCommonUniformValues(
                        renderState,
                        renderState[entitiesStateHolder.entitiesState],
                        renderState[primaryCameraStateHolder.camera],
                        config, materialSystem, entityBuffer
                    )
                    program.setTextureUniforms(graphicsApi, null, batch.material)
                    program.uniforms.entityIndex = batch.entityBufferIndex
                }, block = {
                    geometryBuffer.draw(
                        batch.drawElementsIndirectCommand,
                        primitiveType = PrimitiveType.Triangles,
                        mode = if(config.debug.isDrawLines) RenderingMode.Lines else RenderingMode.Fill,
                        bindIndexBuffer = true,
                    )
                })
            }
        }
    }
}

internal fun Engine.runInteriorMapping() {
    val textureManager = systems.firstIsInstance<TextureManager>()

    world.loadScene {
        addPrimaryCameraControls()
        config.gameDir.apply {
            val cubeMap0 = textureManager.getCubeMap(
                "humus_interior_cubemap",
                listOf(
                    resolve("assets/textures/DallasW/negz.jpg"),
                    resolve("assets/textures/DallasW/posz.jpg"),
                    resolve("assets/textures/DallasW/posy.jpg"), // ceiling
                    resolve("assets/textures/DallasW/negy.jpg"), // floor
                    resolve("assets/textures/DallasW/posx.jpg"),
                    resolve("assets/textures/DallasW/negx.jpg"),
                ),
                true
            )
            val cubeMap1 = textureManager.getCubeMap(
                "humus_interior1_cubemap",
                listOf(
                    resolve("assets/textures/MarriottMadisonWest/negz.jpg"),
                    resolve("assets/textures/MarriottMadisonWest/posz.jpg"),
                    resolve("assets/textures/MarriottMadisonWest/posy.jpg"), // ceiling
                    resolve("assets/textures/MarriottMadisonWest/negy.jpg"), // floor
                    resolve("assets/textures/MarriottMadisonWest/posx.jpg"),
                    resolve("assets/textures/MarriottMadisonWest/negx.jpg"),
                ),
                true
            )
            val interior0 = Material("interior_mapping").apply {
                diffuse.set(1f,0f,0f)
                materialType = Material.MaterialType.FOLIAGE
                cullBackFaces = false
                cullFrontFaces = true
                maps[Material.MAP.ENVIRONMENT] =
                    StaticHandleImpl(cubeMap0, cubeMap0.description, UploadState.Uploaded, 0f)
            }
            val interior1 = interior0.copy(maps = mutableMapOf(Material.MAP.ENVIRONMENT to
                    StaticHandleImpl(cubeMap1, cubeMap1.description, UploadState.Uploaded, 0f)
            ))

            val translations = (0 until 10).flatMap { x ->
                (0 until 10).map { y ->
                    Vector3f(x * 105f, y * 105f, 0f)
                }
            } + (0 until 9).flatMap { z ->
                (0 until 10).map { y ->
                    Vector3f(0f, y * 105f, -105 - (z * 105f))
                }
            }
            val materials = (0..(translations.size/2)).map {
                interior0
            } + (0..(translations.size/2)).map {
                interior1
            }.shuffled()

            // shuffle, because so that entity ids are not in order, because they determine
            // rotation of interior maps
            translations.shuffled().forEachIndexed { index, translation ->
                addStaticModelEntity(
                    "Interior", "assets/models/cube.obj",
                    scale = Vector3f(100f),
                    translation = translation
                ).apply {
                    add(MaterialComponent().apply { this.material = materials[index] })
                    add(InvisibleComponent())
                    add(InteriorMappingComponent())
                }
            }

            val firstWallMaterialComponent = MaterialComponent().apply {
                material = Material("FirstWall").apply {
                    diffuse.set(0.2f)
                    materialType = Material.MaterialType.UNLIT
                    cullBackFaces = false
                    cullFrontFaces = false
                    uvScale = Vector2f(0.2f)
                    worldSpaceTexCoords = WorldSpaceTexCoords.XY
                    val brick =
                        textureManager.getStaticTextureHandle("/assets/textures/brick.png", true, config.gameDir, TextureFilterConfig(minFilter = MinFilter.LINEAR))
                    graphicsApi.run {
                        brick.uploadAsync()
                    }
                    maps[Material.MAP.DIFFUSE] = brick.handle
                }
            }
            // second wall
            val sideWardsPlanesCount = 11
            repeat(sideWardsPlanesCount) {
                val isFirst = it == 0
                val isLast = it == sideWardsPlanesCount - 1
                addStaticModelEntity(
                    "Plane0_$it", "assets/models/plane.obj",
                    rotation = AxisAngle4f(1.57f, 1f, 0f, 0f),
                    scale = Vector3f(520f, 1f, if(isFirst) 5f else 20f),
                    translation = Vector3f(475f, -60 + it * 105f, 50f)
                ).apply {
                    add(firstWallMaterialComponent)
                }
            }
            val downWardsPlanesCount = 11
            repeat(downWardsPlanesCount) {
                val isFirst = it == 0
                val isLast = it == downWardsPlanesCount - 1
                val xOffset = when {
                    isFirst -> -45
                    isLast -> -60
                    else -> -50
                }
                addStaticModelEntity(
                    "Plane1_$it", "assets/models/plane.obj",
                    rotation = AxisAngle4f(2*1.57f, 0f, 0f, 1f),
                    rotation1 = AxisAngle4f(1.57f, 1f, 0f, 0f),
                    scale = Vector3f(if(isFirst || isLast) 5f else 20f, 1f, 625f),
                    translation = Vector3f(xOffset + it * 105f, 385f, 50f)
                ).apply {
                    add(firstWallMaterialComponent)
                }
            }

            // second wall
            val secondWallMaterialComponent = MaterialComponent().apply {
                material = firstWallMaterialComponent.material.copy(
                    name = "SecondWall",
                    worldSpaceTexCoords = WorldSpaceTexCoords.ZY
                )
            }
            repeat(sideWardsPlanesCount) {
                val isFirst = it == 0
                val isLast = it == sideWardsPlanesCount - 1
                addStaticModelEntity(
                    "Plane0_$it", "assets/models/plane.obj",
                    rotation = AxisAngle4f(1.57f, 1f, 0f, 0f),
                    rotation1 = AxisAngle4f(1.57f, 0f, 1f, 0f),
                    scale = Vector3f(520f, 1f, if(isFirst) 5f else 20f),
                    translation = Vector3f(-50f, -60 + it * 105f, -470f)
                ).apply {
                    add(secondWallMaterialComponent)
                }
            }
            repeat(downWardsPlanesCount) {
                val isFirst = it == 0
                val isLast = it == downWardsPlanesCount - 1
                val zOffset = when {
                    isFirst -> -45
                    isLast -> -60
                    else -> -50
                }
                addStaticModelEntity(
                    "Plane1_$it", "assets/models/plane.obj",
                    rotation = AxisAngle4f(2*1.57f, 0f, 0f, 1f),
                    rotation1 = AxisAngle4f(1.57f, 1f, 0f, 0f),
                    rotation2 = AxisAngle4f(1.57f, 0f, 1f, 0f),
                    scale = Vector3f(if(isFirst || isLast) 5f else 20f, 1f, 625f),
                    translation = Vector3f(-50f, 385f, -zOffset - (it * 105f))
                ).apply {
                    add(secondWallMaterialComponent)
                }
            }

            addStaticModelEntity(
                "Ground", "assets/models/plane.obj",
                scale = Vector3f(1000f),
                translation = Vector3f(500f, -51f, -280f)
            ).apply {
                add(MaterialComponent().apply {
                    material = Material("ground_plane").apply {
                        diffuse.set(0.2f)
                    }
                })
            }
        }
    }
    simulate()
}
class InteriorMappingComponent: Component()