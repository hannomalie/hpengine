package de.hanno.hpengine.extension

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.*
import de.hanno.hpengine.camera.CameraRenderExtension
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditor
import de.hanno.hpengine.graphics.imgui.editor.primaryCamera
import de.hanno.hpengine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.graphics.texture.OpenGLTexture2D
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.scene.OceanWaterRenderSystem
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.graphics.fps.FPSCounterSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.imgui.editor.EntityClickListener
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditorExtension
import de.hanno.hpengine.graphics.light.area.AreaLightStateHolder
import de.hanno.hpengine.graphics.light.directional.DirectionalLightStateHolder
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.*
import de.hanno.hpengine.graphics.renderer.extensions.*
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import de.hanno.hpengine.graphics.state.*
import de.hanno.hpengine.graphics.state.PointLightStateHolder
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.scene.WorldAABBStateHolder
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.lwjgl.opengl.GL30
import kotlin.collections.set

data class IdTexture(val texture: OpenGLTexture2D) // TODO: Move to a proper place
data class SharedDepthBuffer(val depthBuffer: DepthBuffer<*>)

val deferredRendererModule = module {
    renderSystem {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                ExtensibleDeferredRenderer(
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    getAll<DeferredRenderExtension>().distinct(),
                    get(),
                    get(),
                    get(),
                )
            }
        }
    }
    single {
        val config: Config = get()
        get<GpuContext>().run {
            SharedDepthBuffer(DepthBuffer(config.width, config.height))
        }
    }
    single {
        val config: Config = get()
        val sharedDepthBuffer: SharedDepthBuffer = get()
        get<GpuContext>().run {
            DeferredRenderingBuffer(
                config.width,
                config.height,
                sharedDepthBuffer.depthBuffer
            )
        }
    }
    single {
        val deferredRenderingBuffer: DeferredRenderingBuffer = get()
        IdTexture(deferredRenderingBuffer.depthAndIndicesMap)
    }
    single {
        val deferredRenderingBuffer: DeferredRenderingBuffer = get()
        FinalOutput(deferredRenderingBuffer.finalMap)
    }
    single { DebugOutput(null, 0) }
    single { DeferredRenderExtensionConfig(getAll<DeferredRenderExtension>().distinct()) }
}

val imGuiEditorModule = module {
    renderSystem {
        val finalOutput: FinalOutput = get()

        get<GpuContext>().run {
            get<RenderStateContext>().run {
                ImGuiEditor(
                    get(),
                    get(),
                    finalOutput,
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    get(),
                    getAll<ImGuiEditorExtension>().distinct(),
                    get(),
                    get(),
                )
            }
        }
    }
    single {
        EntityClickListener()
    } binds arrayOf(EntityClickListener::class, OnClickListener::class)
}
val textureRendererModule = module {
    single {
        val config: Config = get()

        get<GpuContext>().run {
            RenderTarget(
                OpenGLFrameBuffer(
                    depthBuffer = DepthBuffer(config.width, config.height)
                ),
                width = config.width,
                height = config.height,
                textures = listOf(
                    ColorAttachmentDefinition("Color", GL30.GL_RGBA8)
                ).toTextures(
                    config.width, config.height
                ),
                name = "Final Image"
            )
        }
    }
    single {
        val textureManager: OpenGLTextureManager = get()
        IdTexture(textureManager.defaultTexture.backingTexture)
    }
    single {
        val renderTarget: RenderTarget2D = get()
        FinalOutput(renderTarget.textures.first())
    }
    renderSystem {
        val textureManager: OpenGLTextureManager = get()
        val renderTarget: RenderTarget2D = get()

            object : SimpleTextureRenderer(get<GpuContext>(), get(), textureManager.defaultTexture.backingTexture, get(), get()) {
                override val sharedRenderTarget = renderTarget
                override val requiresClearSharedRenderTarget = true

                override fun render(renderState: RenderState) {
                    gpuContext.clearColor(1f, 0f, 0f, 1f)
                    drawToQuad(texture = texture)
                }
            }
    }
}
val baseModule = module {
    renderSystem { FPSCounterSystem() }
    single {
        val system: FPSCounterSystem = get()
        system.fpsCounter
    }
    single {
        get<GpuContext>().run {
            RenderManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), getAll())
        }
    }
    single { OpenGlProgramManager(get(), get()) } binds arrayOf(
        ProgramManager::class,
        OpenGlProgramManager::class,
    )
    single {
        get<GpuContext>().run {
            OpenGLTextureManager(get(), get())
        }
    } binds arrayOf(
        TextureManager::class,
        OpenGLTextureManager::class,
    )

    single { GlfwWindow(get()) } bind Window::class

    addBackendModule()
    addCameraModule()
    addReflectionProbeModule()
    addSkyboxModule()
    addPointLightModule()
    addGIModule()
    addOceanWaterModule()
    addDirectionalLightModule()

    single { KotlinComponentSystem() } binds arrayOf(
        KotlinComponentSystem::class,
        ImGuiEditorExtension::class
    )

    renderExtension {
        get<GpuContext>().run {
            ForwardRenderExtension(get(), get(), get(), get(), get(), get())
        }
    }
    renderExtension { AOScatteringExtension(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    renderExtension {
        get<GpuContext>().run {
            PixelPerfectPickingExtension(get(), get(), getAll())
        }
    }
//    TODO: Fails because of shader code errors
//    renderExtension { EvaluateProbeRenderExtension(get(), get(), get(), get(), get()) }

    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                PointLightStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                DirectionalLightStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                EntitiesStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                EnvironmentProbesStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                SkyBoxStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                WorldAABBStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                PrimaryCameraStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                AreaLightStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                ReflectionProbesStateHolder()
            }
        }
    }
    single {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                GiVolumeStateHolder()
            }
        }
    }
}

fun Module.addGIModule() {
    renderExtension {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                VoxelConeTracingExtension(get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
            }
        }
    }
}

fun Module.addPointLightModule() {
    renderExtension {
        get<GpuContext>().run {
            BvHPointLightSecondPassExtension(get(), get(), get(), get(), get(), get(), get(), get())
        }
    }
}

fun Module.addDirectionalLightModule() {
    renderExtension {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                DirectionalLightShadowMapExtension(get(), get(), get(), get(), get())
            }
        }
    }
    renderExtension { DirectionalLightSecondPassExtension(get(), get(), get(), get(), get(), get(), get()) }
}

fun Module.addOceanWaterModule() {
    renderSystem {
        get<GpuContext>().run {
            OceanWaterRenderSystem(get(), get(), get(), get(), get())
        }
    }
}

fun Module.addReflectionProbeModule() {
    renderExtension {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                ReflectionProbeRenderExtension(get(), get(), get(), get(), get(), get(), get(), get(), get())
            }
        }
    }
}

fun Module.addCameraModule() {
    renderExtension {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                CameraRenderExtension(get(), get(), get())
            }
        }
    }
}

fun Module.addSkyboxModule() {
    renderExtension {
        get<GpuContext>().run {
            get<RenderStateContext>().run {
                SkyboxRenderExtension(get(), get(), get(), get(), get(), get())
            }
        }
    }
}
fun Module.addBackendModule() {
    single { AddResourceContext() }
    single { OpenGLContext(get()) } bind GpuContext::class
    single { Input(get()) }
    single { RenderSystemsConfig(getAll()) }
    single {
        get<GpuContext>().run {
            RenderStateContext { RenderState() }
        }
    }
}

@All(GiVolumeComponent::class)
class GiVolumeSystem(
    val textureManager: OpenGLTextureManager,
) : BaseEntitySystem() {
    private val grids = mutableMapOf<Int, VoxelConeTracingExtension.GIVolumeGrids>()
    override fun inserted(entityId: Int) {
        grids[entityId] = textureManager.createGIVolumeGrids()
    }

    override fun removed(entityId: Int) {
        grids.remove(entityId)
    }

    override fun processSystem() {}

    // TODO: Implement extraction to a GiVolumeStateHolder here
}

context(GpuContext, RenderStateContext)
class GiVolumeStateHolder {
    val giVolumesState = renderState.registerState {
        GiVolumesState()
    }
}
class GiVolumesState {
    // Implement needed state here, like transform etc
    val volumes = listOf<Unit>()
}

class SkyBoxComponent : Component()

@All(SkyBoxComponent::class)
class SkyBoxSystem : BaseEntitySystem(), WorldPopulator {
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var tagManager: TagManager

    @Wire
    lateinit var config: ConfigImpl

    override fun processSystem() {
        if (!tagManager.isRegistered(primaryCamera)) return

        val primaryCameraEntityId = tagManager.getEntityId(primaryCamera)

        if (subscription.entities.size() > 0) {
            subscription.entities.data.firstOrNull()?.let { skyBoxEntityId ->
                val transform = transformComponentMapper[skyBoxEntityId].transform
                val primaryCameraTransform = transformComponentMapper[primaryCameraEntityId].transform

                val eyePosition = primaryCameraTransform.position
                transform.identity().translate(eyePosition)
                transform.scale(1000f)
            }
        }
    }

    override fun World.populate() {
        addSkyBox(config)
    }
}

context(GpuContext, RenderStateContext)
class SkyBoxStateHolder {
    // TODO: Actually write this
    val skyBoxMaterialIndex = renderState.registerState { -1 }
}

fun World.addSkyBox(config: Config) {
    edit(create()).apply {
        create(NameComponent::class.java).apply {
            name = "SkyBox"
        }
        create(TransformComponent::class.java)
        create(SkyBoxComponent::class.java)
        create(ModelComponent::class.java).apply {
            modelComponentDescription = StaticModelComponentDescription(
                "assets/models/skybox.obj",
                Directory.Engine,
            )
        }
        create(MaterialComponent::class.java).apply {
            material = Material(
                name = "Skybox",
                materialType = Material.MaterialType.UNLIT,
                cullBackFaces = false,
                renderPriority = -1,
                writesDepth = false,
                depthTest = true,
                isShadowCasting = false,
                programDescription = ProgramDescription(
                    vertexShaderSource = config.EngineAsset("shaders/first_pass_vertex.glsl")
                        .toCodeSource(),
                    fragmentShaderSource = config.EngineAsset("shaders/first_pass_fragment.glsl")
                        .toCodeSource().enhanced(
                            name = "skybox_first_pass_fragment",
                            replacements = arrayOf(
                                "//END" to """
                            out_colorMetallic.rgb = 0.25f*textureLod(environmentMap, V, 0).rgb;
                        """.trimIndent()
                            )
                        )
                )
            ).apply {
                this.put(Material.MAP.ENVIRONMENT, getSystem(OpenGLTextureManager::class.java).cubeMap)
            }
        }
    }
}
