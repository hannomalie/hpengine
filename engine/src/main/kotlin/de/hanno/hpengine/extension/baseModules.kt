package de.hanno.hpengine.extension

import InternalTextureFormat.RGBA8
import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.World
import com.artemis.annotations.All
import com.artemis.annotations.Wire
import com.artemis.managers.TagManager
import de.hanno.hpengine.WorldPopulator
import de.hanno.hpengine.artemis.GiVolumeComponent
import de.hanno.hpengine.artemis.model.MaterialComponent
import de.hanno.hpengine.artemis.model.ModelComponent
import de.hanno.hpengine.component.NameComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.GlfwWindow
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.OpenGLContext
import de.hanno.hpengine.graphics.fps.FPSCounterSystem
import de.hanno.hpengine.graphics.output.DebugOutput
import de.hanno.hpengine.graphics.output.FinalOutput
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.*
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRendererModule
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.createGIVolumeGrids
import de.hanno.hpengine.graphics.rendertarget.ColorAttachmentDefinition
import de.hanno.hpengine.graphics.rendertarget.RenderTarget2D
import de.hanno.hpengine.graphics.rendertarget.SharedDepthBuffer
import de.hanno.hpengine.graphics.rendertarget.toTextures
import de.hanno.hpengine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.texture.OpenGLTextureManager
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.texture.TextureManagerBaseSystem
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.model.material.Material
import de.hanno.hpengine.model.material.ProgramDescription
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.ressources.FileMonitor
import de.hanno.hpengine.ressources.enhanced
import de.hanno.hpengine.scene.dsl.Directory
import de.hanno.hpengine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.stopwatch.OpenGLGPUProfiler
import org.joml.Vector4f
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.module
import org.koin.ksp.generated.module
import kotlin.collections.set

val textureRendererModule = module {
    single {
        val config: Config = get()

        get<GraphicsApi>().run {
            RenderTarget(
                frameBuffer = FrameBuffer(
                    depthBuffer = DepthBuffer(config.width, config.height)
                ),
                width = config.width,
                height = config.height,
                textures = listOf(
                    ColorAttachmentDefinition("Color", RGBA8)
                ).toTextures(
                    this,
                    config.width, config.height
                ),
                name = "Final Image",
                clear = Vector4f(),
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

            object : SimpleTextureRenderer(get<GraphicsApi>(), get(), textureManager.defaultTexture.backingTexture, get(), get()) {
                override val sharedRenderTarget = renderTarget
                override val requiresClearSharedRenderTarget = true

                override fun render(renderState: RenderState) {
                    graphicsApi.clearColor(1f, 0f, 0f, 1f)
                    drawToQuad(texture = texture)
                }
            }
    }
}
val engineModule = EngineModule().module

@Module
@ComponentScan
class EngineModule {
    @Single
    fun fpsCounter(fpsCounterSystem: FPSCounterSystem) = fpsCounterSystem.fpsCounter
    @Single
    fun fpsCounterSystem() = FPSCounterSystem()

    @Single(binds = [Window::class])
    fun glfwWindow(config: Config, profiler: GPUProfiler) = GlfwWindow(config, profiler)
}

val openglModule = OpenGLModule().module

@Module
class OpenGLModule {
    @Single(binds = [GraphicsApi::class])
    fun openGLContext(window: Window, config: Config) = OpenGLContext(window, config)
    @Single(binds = [GPUProfiler::class, OpenGLGPUProfiler::class])
    fun openGLProfiler(config: Config) = OpenGLGPUProfiler(config.debug::profiling)
    @Single(binds = [
        TextureManager::class,
        OpenGLTextureManager::class,
        TextureManagerBaseSystem::class,
    ])
    fun openglTextureManager(
        config: Config, graphicsApi: GraphicsApi, programManager: OpenGlProgramManager
    ) = OpenGLTextureManager(config, graphicsApi, programManager)

    @Single(binds = [
        ProgramManager::class,
        OpenGlProgramManager::class,
    ])
    fun openglProgramManager(
        graphicsApi: GraphicsApi,
        fileMonitor: FileMonitor,
        config: Config,
    ) = OpenGlProgramManager(graphicsApi, fileMonitor, config,)
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

@Single
class GiVolumeStateHolder(
    renderStateContext: RenderStateContext
) {
    val giVolumesState = renderStateContext.renderState.registerState {
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
    lateinit var config: Config

    override fun processSystem() {
        if (!tagManager.isRegistered(primaryCameraTag)) return

        val primaryCameraEntityId = tagManager.getEntityId(primaryCameraTag)

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

@Single
class SkyBoxStateHolder(
    private val renderStateContext: RenderStateContext
) {
    // TODO: Actually write this
    val skyBoxMaterialIndex = renderStateContext.renderState.registerState { -1 }
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
