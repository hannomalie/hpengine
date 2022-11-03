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
import de.hanno.hpengine.backend.Backend
import de.hanno.hpengine.backend.OpenGlBackend
import de.hanno.hpengine.camera.CameraRenderExtension
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.config.ConfigImpl
import de.hanno.hpengine.bus.EventBus
import de.hanno.hpengine.bus.MBassadorEventBus
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditor
import de.hanno.hpengine.graphics.imgui.editor.primaryCamera
import de.hanno.hpengine.graphics.renderer.DeferredRenderExtensionConfig
import de.hanno.hpengine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DirectionalLightShadowMapExtension
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.model.texture.Texture2D
import de.hanno.hpengine.model.texture.TextureManager
import de.hanno.hpengine.scene.AddResourceContext
import de.hanno.hpengine.scene.OceanWaterRenderSystem
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.graphics.fps.FPSCounterSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditorExtension
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.*
import de.hanno.hpengine.graphics.renderer.rendertarget.*
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module
import org.lwjgl.opengl.GL30
import javax.swing.SwingUtilities
import kotlin.collections.set

data class IdTexture(val texture: Texture2D) // TODO: Move to a proper place
data class SharedDepthBuffer(val depthBuffer: DepthBuffer<*>)

val deferredRendererModule = module {
    renderSystem { ExtensibleDeferredRenderer(get(), get(), get(), get(), get(), get(), getAll<DeferredRenderExtension>().distinct()) }
    single {
        val config: Config = get()
        SharedDepthBuffer(DepthBuffer(get(), config.width, config.height))
    }
    single {
        val config: Config = get()
        val gpuContext: GpuContext = get()
        val sharedDepthBuffer: SharedDepthBuffer = get()
        DeferredRenderingBuffer(
            gpuContext,
            config.width,
            config.height,
            sharedDepthBuffer.depthBuffer
        )
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
        val gpuContext: GpuContext = get()
        val finalOutput: FinalOutput = get()
        ImGuiEditor(get(), gpuContext, get(), finalOutput, get(), get(), get(), get(), getAll<DeferredRenderExtension>().distinct(), get(), get(), getAll<ImGuiEditorExtension>().distinct())
    }
}
val textureRendererModule = module {
    single {
        val config: Config = get()
        val gpuContext: GpuContext = get()

        RenderTarget(
            gpuContext,
            FrameBuffer(
                gpuContext,
                depthBuffer = DepthBuffer(gpuContext, config.width, config.height)
            ),
            name = "Final Image",
            width = config.width,
            height = config.height,
            textures = listOf(
                ColorAttachmentDefinition("Color", GL30.GL_RGBA8)
            ).toTextures(
                gpuContext, config.width, config.height
            )
        )

    }
    single {
        val textureManager: TextureManager = get()
        IdTexture(textureManager.defaultTexture.backingTexture)
    }
    single {
        val renderTarget: RenderTarget2D = get()
        FinalOutput(renderTarget.textures.first())
    }
    renderSystem {
        val textureManager: TextureManager = get()
        val renderTarget: RenderTarget2D = get()
        val gpuContext: GpuContext = get()

        object : SimpleTextureRenderer(get(), get(), textureManager.defaultTexture.backingTexture, get(), get()) {
            override val sharedRenderTarget = renderTarget
            override val requiresClearSharedRenderTarget = true
            override lateinit var artemisWorld: World

            override fun render(result: DrawResult, renderState: RenderState) {
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
    single { RenderManager(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), getAll()) }
    single { OpenGlProgramManager(get(), get(), get()) } binds arrayOf(
        ProgramManager::class,
        OpenGlProgramManager::class
    )
    single { TextureManager(get(), get(), get()) }

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

    renderExtension { ForwardRenderExtension(get(), get(), get(), get()) }
    renderExtension { AOScatteringExtension(get(), get(), get(), get(), get()) }
//    TODO: Fails because of shader code errors
//    renderExtension { EvaluateProbeRenderExtension(get(), get(), get(), get(), get()) }
}

fun Module.addGIModule() {
    renderExtension { VoxelConeTracingExtension(get(), get(), get(), get(), get(), get()) }
}

fun Module.addPointLightModule() {
    renderExtension { BvHPointLightSecondPassExtension(get(), get(), get(), get(), get()) }
}

fun Module.addDirectionalLightModule() {
    renderExtension { DirectionalLightShadowMapExtension(get(), get(), get(), get(), get()) }
    renderExtension { DirectionalLightSecondPassExtension(get(), get(), get(), get(), get()) }
}

fun Module.addOceanWaterModule() {
    renderSystem { OceanWaterRenderSystem(get(), get(), get(), get(), get()) }
}

fun Module.addReflectionProbeModule() {
    renderExtension { ReflectionProbeRenderExtension(get(), get(), get(), get(), get(), get()) }
}

fun Module.addCameraModule() {
    renderExtension { CameraRenderExtension(get(), get(), get(), get()) }
}

fun Module.addSkyboxModule() {
    renderExtension {
        SkyboxRenderExtension(get(), get(), get(), get(), get(), get())
    }
}

fun Module.addBackendModule() {
    single { AddResourceContext() }
    single { MBassadorEventBus() } bind EventBus::class

    single { OpenGLContext.invoke(get()) } bind GpuContext::class
    single { Input(get()) }
    single { OpenGlBackend(get(), get(), get(), get(), get(), get()) } bind Backend::class
    single { RenderSystemsConfig(getAll()) }
    single {
        val gpuContext: GpuContext = get()
        RenderStateManager { RenderState(gpuContext) }
    }
}

// TODO: Don't duplicate, move to core
object SwingUtils {
    fun invokeLater(block: () -> Unit) = if (SwingUtilities.isEventDispatchThread()) {
        block()
    } else {
        SwingUtilities.invokeLater(block)
    }

    fun <T> invokeAndWait(block: () -> T) = if (SwingUtilities.isEventDispatchThread()) {
        block()
    } else {
        var result: T? = null
        SwingUtilities.invokeAndWait {
            result = block()
        }
        result!!
    }
}

@All(GiVolumeComponent::class)
class GiVolumeSystem(
    val textureManager: TextureManager,
) : BaseEntitySystem() {
    private val grids = mutableMapOf<Int, VoxelConeTracingExtension.GIVolumeGrids>()
    override fun inserted(entityId: Int) {
        grids[entityId] = textureManager.createGIVolumeGrids()
    }

    override fun removed(entityId: Int) {
        grids.remove(entityId)
    }

    override fun processSystem() {}
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
                this.put(Material.MAP.ENVIRONMENT, getSystem(TextureManager::class.java).cubeMap)
            }
        }
    }
}
