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
import de.hanno.hpengine.graphics.texture.*
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.*
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.model.material.WorldSpaceTexCoords
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.skybox.SkyBoxStateHolder
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
                InteriorMappingRenderSystem()
            } binds arrayOf(BaseSystem::class, InteriorMappingRenderSystem::class)
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
class InteriorMappingRenderSystem: BaseEntitySystem() {

    private val entityIds = mutableListOf<Int>()
    override fun processSystem() {
        entityIds.clear()
        entityIds.addAll(mapEntity { it }) // move to extract
    }
}

internal fun Engine.runInteriorMapping() {
    val textureManager = systems.firstIsInstance<TextureManager>()

    world.loadScene {
//        addPrimaryCameraControls()
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

//            val firstWallMaterialComponent = MaterialComponent().apply {
//                material = Material("FirstWall").apply {
//                    diffuse.set(0.2f)
//                    materialType = Material.MaterialType.UNLIT
//                    cullBackFaces = false
//                    cullFrontFaces = false
//                    uvScale = Vector2f(0.2f)
//                    worldSpaceTexCoords = WorldSpaceTexCoords.XY
//                    val brick =
//                        textureManager.getStaticTextureHandle("/assets/textures/brick.png", true, config.gameDir, TextureFilterConfig(minFilter = MinFilter.LINEAR))
//                    graphicsApi.run {
//                        brick.uploadAsync()
//                    }
//                    maps[Material.MAP.DIFFUSE] = brick.handle
//                }
//            }
//            // second wall
//            val sideWardsPlanesCount = 11
//            repeat(sideWardsPlanesCount) {
//                val isFirst = it == 0
//                val isLast = it == sideWardsPlanesCount - 1
//                addStaticModelEntity(
//                    "Plane0_$it", "assets/models/plane.obj",
//                    rotation = AxisAngle4f(1.57f, 1f, 0f, 0f),
//                    scale = Vector3f(520f, 1f, if(isFirst) 5f else 20f),
//                    translation = Vector3f(475f, -60 + it * 105f, 50f)
//                ).apply {
//                    add(firstWallMaterialComponent)
//                }
//            }
//            val downWardsPlanesCount = 11
//            repeat(downWardsPlanesCount) {
//                val isFirst = it == 0
//                val isLast = it == downWardsPlanesCount - 1
//                val xOffset = when {
//                    isFirst -> -45
//                    isLast -> -60
//                    else -> -50
//                }
//                addStaticModelEntity(
//                    "Plane1_$it", "assets/models/plane.obj",
//                    rotation = AxisAngle4f(2*1.57f, 0f, 0f, 1f),
//                    rotation1 = AxisAngle4f(1.57f, 1f, 0f, 0f),
//                    scale = Vector3f(if(isFirst || isLast) 5f else 20f, 1f, 625f),
//                    translation = Vector3f(xOffset + it * 105f, 385f, 50f)
//                ).apply {
//                    add(firstWallMaterialComponent)
//                }
//            }
//
//            // second wall
//            val secondWallMaterialComponent = MaterialComponent().apply {
//                material = firstWallMaterialComponent.material.copy(
//                    name = "SecondWall",
//                    worldSpaceTexCoords = WorldSpaceTexCoords.ZY
//                )
//            }
//            repeat(sideWardsPlanesCount) {
//                val isFirst = it == 0
//                val isLast = it == sideWardsPlanesCount - 1
//                addStaticModelEntity(
//                    "Plane0_$it", "assets/models/plane.obj",
//                    rotation = AxisAngle4f(1.57f, 1f, 0f, 0f),
//                    rotation1 = AxisAngle4f(1.57f, 0f, 1f, 0f),
//                    scale = Vector3f(520f, 1f, if(isFirst) 5f else 20f),
//                    translation = Vector3f(-50f, -60 + it * 105f, -470f)
//                ).apply {
//                    add(secondWallMaterialComponent)
//                }
//            }
//            repeat(downWardsPlanesCount) {
//                val isFirst = it == 0
//                val isLast = it == downWardsPlanesCount - 1
//                val zOffset = when {
//                    isFirst -> -45
//                    isLast -> -60
//                    else -> -50
//                }
//                addStaticModelEntity(
//                    "Plane1_$it", "assets/models/plane.obj",
//                    rotation = AxisAngle4f(2*1.57f, 0f, 0f, 1f),
//                    rotation1 = AxisAngle4f(1.57f, 1f, 0f, 0f),
//                    rotation2 = AxisAngle4f(1.57f, 0f, 1f, 0f),
//                    scale = Vector3f(if(isFirst || isLast) 5f else 20f, 1f, 625f),
//                    translation = Vector3f(-50f, 385f, -zOffset - (it * 105f))
//                ).apply {
//                    add(secondWallMaterialComponent)
//                }
//            }
            val skyBoxTexture = StaticHandleImpl(
                textureManager.getCubeMap(
                    "assets/textures/skybox/skybox.png",
                    config.directories.engineDir.resolve("assets/textures/skybox/skybox.png")
                ), uploadState = UploadState.Uploaded, currentMipMapBias = 0f
            )
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
            val bricks = textureManager.getStaticTextureHandle(
                "assets/textures/brick.png",
                true,
                config.directories.gameDir,
            ).apply {
                graphicsApi.run {
                    uploadAsync()
                }
            }
            val interiorMaterial = Material("Interior0").apply {
                diffuse.set(0.2f)
                roughness = 0.5f
                maps[Material.MAP.DIFFUSE] = textureManager.defaultTexture // bricks
                maps[Material.MAP.NORMAL] = bricks
                maps[Material.MAP.ENVIRONMENT] =
                    StaticHandleImpl(cubeMap0, cubeMap0.description, UploadState.Uploaded, 0f)
                maps[Material.MAP.ENVIRONMENT0] =
                    StaticHandleImpl(cubeMap1, cubeMap1.description, UploadState.Uploaded, 0f)
                maps[Material.MAP.ENVIRONMENT1] = skyBoxTexture
                programDescription = ProgramDescription(
                    config.engineDir.resolve("shaders/color_only/color_out_fragment.glsl")
                        .toCodeSource()
                        .enhanced(
                            "interior_plane_fragment",
                            fileBasedReplacements = arrayOf(
                                "//END" to config.gameDir.resolve("shaders/interior_mapping_include.glsl")
                            )
                        ),
                    config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
                )
            }
            addStaticModelEntity(
                "InteriorPlane0", "assets/models/plane.obj",
                scale = Vector3f(520f, 1f, 517f),
                rotation = AxisAngle4f(1.57f, 0f, 0f, 1f),
                rotation1 = AxisAngle4f(1.57f, 0f, 1f, 0f),
                translation = Vector3f(477f, 460f, 48f)
            ).apply {
                add(MaterialComponent().apply {
                    material = interiorMaterial
                })
            }
            addStaticModelEntity(
                "InteriorPlane1", "assets/models/plane.obj",
                scale = Vector3f(520f, 1f, 517f),
                rotation = AxisAngle4f(1.57f, 0f, 0f, 1f),
                translation = Vector3f(-40f, 460f, -466f)
            ).apply {
                add(MaterialComponent().apply {
                    material = interiorMaterial.copy(
                        name = "Interior1",
                        worldSpaceTexCoords = WorldSpaceTexCoords.ZY
                    )
                })
            }
//            val glassMaterial = Material("glass").apply {
//                 // TODO: Verify if just setting this is okay
//
//                diffuse.set(1f)
//                materialType = Material.MaterialType.UNLIT
//                transparencyType = Material.TransparencyType.FULL
//                transparency = 0.6f
//                cullBackFaces = false
//                cullFrontFaces = false
//                roughness = 0f
//                metallic = 1f
//                writesDepth = false
//                maps[Material.MAP.ENVIRONMENT] = cubeMap
//                programDescription = ProgramDescription(
//                    config.engineDir.resolve("shaders/color_only/color_out_fragment.glsl")
//                        .toCodeSource()
//                        .enhanced(
//                            "glass_fragment", arrayOf(
//                                "//END" to """
//                                    out_color.rgba = vec4(texture(environmentMap, reflect(V, normal_world)).rgb, 1-material.transparency);
//                                    """.trimIndent()
//                            )
//                        ),
//                    config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
//                )
//            }
//            addStaticModelEntity(
//                "Glass0", "assets/models/plane.obj",
//                scale = Vector3f(520f, 1f, 517f),
//                rotation = AxisAngle4f(1.57f, 0f, 0f, 1f),
//                rotation1 = AxisAngle4f(1.57f, 0f, 1f, 0f),
//                translation = Vector3f(477f, 460f, 49f)
//            ).apply {
//                add(MaterialComponent().apply {
//                    material = glassMaterial
//                })
//            }
//            addStaticModelEntity(
//                "Glass1", "assets/models/plane.obj",
//                scale = Vector3f(520f, 1f, 517f),
//                rotation = AxisAngle4f(1.57f, 0f, 0f, 1f),
//                translation = Vector3f(-48f, 460f, -467f)
//            ).apply {
//                add(MaterialComponent().apply {
//                    material = glassMaterial
//                })
//            }
        }
    }
    simulate()
}
class InteriorMappingComponent: Component()