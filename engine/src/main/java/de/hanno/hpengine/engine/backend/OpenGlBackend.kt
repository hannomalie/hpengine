package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.EngineAsset
import de.hanno.hpengine.engine.directory.EngineAsset.Companion.toAsset
import de.hanno.hpengine.engine.directory.GameAsset
import de.hanno.hpengine.engine.directory.GameAsset.Companion.toAsset
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Extension
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import java.util.concurrent.CopyOnWriteArrayList

interface OpenGl: BackendType {
    val gpuContext: OpenGLContext
}

class OpenGlBackend(override val eventBus: EventBus,
                    override val gpuContext: GpuContext<OpenGl>,
                    override val programManager: ProgramManager<OpenGl>,
                    override val textureManager: TextureManager,
                    override val input: Input,
                    override val addResourceContext: AddResourceContext) : Backend<OpenGl> {

    companion object {
        operator fun invoke(window: Window<OpenGl>, config: Config): OpenGlBackend {
            val singleThreadContext = AddResourceContext()
            val eventBus = MBassadorEventBus()
            val gpuContext = OpenGLContext.invoke(window)
            val programManager = OpenGlProgramManager(gpuContext, eventBus, config)
            val textureManager = TextureManager(config, programManager, gpuContext, singleThreadContext)
            val input = Input(eventBus, gpuContext)

            return OpenGlBackend(eventBus, gpuContext, programManager, textureManager, input, singleThreadContext)
        }
    }
}

class EngineContext(
    val config: Config,
    var additionalExtensions: List<Extension> = emptyList(), // TODO: Make this not var...but editor needs context, cyclic reference
    val window: Window<OpenGl> = GlfwWindow(config.width, config.height, "HPEngine", config.performance.isVsync),
    val backend: Backend<OpenGl> = OpenGlBackend(window, config),
    val deferredRenderingBuffer: DeferredRenderingBuffer = DeferredRenderingBuffer(backend.gpuContext, config.width, config.height),
    val renderSystems: MutableList<RenderSystem> = CopyOnWriteArrayList(),
    val renderStateManager: RenderStateManager = RenderStateManager { RenderState(backend.gpuContext) },
    val materialManager: MaterialManager = MaterialManager(config, backend.eventBus, backend.textureManager, backend.addResourceContext)) {

    inline val engineDir
        get() = config.engineDir
    inline val gameDir
        get() = config.gameDir

    fun EngineAsset(relativePath: String): EngineAsset = config.EngineAsset(relativePath)
    fun GameAsset(relativePath: String): GameAsset = config.GameAsset(relativePath)

    fun update(deltaSeconds: Float) {
        backend.gpuContext.update(deltaSeconds)
        backend.programManager.update(deltaSeconds)
    }

    fun extract(scene: Scene, renderState: RenderState) {
        scene.extract(renderState)
        renderSystems.forEach { it.extract(scene, renderState) }
    }
    fun add(extension: Extension) {
        additionalExtensions = additionalExtensions + listOf(extension)
    }

    fun onEntityAdded(scene: Scene, entities: List<Entity>) {
        additionalExtensions.forEach {
            // TODO: Add support for other hooks
            it.manager?.onEntityAdded(entities)
        }
    }

    fun afterSetScene(lastScene: Scene, currentScene: Scene) {
        additionalExtensions.forEach {
            // TODO: Add support for other hooks
            it.manager?.afterSetScene(lastScene, currentScene)
        }
    }
}

inline val EngineContext.addResourceContext: AddResourceContext
    get() = backend.addResourceContext
inline val EngineContext.input: Input
    get() = backend.input
inline val EngineContext.textureManager: TextureManager
    get() = backend.textureManager
inline val EngineContext.programManager: ProgramManager<OpenGl>
    get() = backend.programManager
inline val EngineContext.gpuContext: GpuContext<OpenGl>
    get() = backend.gpuContext
inline val EngineContext.eventBus
    get() = backend.eventBus
