package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GlfwWindow
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
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

class EngineContext(val config: Config,
                        val window: Window<OpenGl> = GlfwWindow(config.width, config.height, "HPEngine", config.performance.isVsync),
                        val backend: Backend<OpenGl> = OpenGlBackend(window, config),
                        val deferredRenderingBuffer: DeferredRenderingBuffer = DeferredRenderingBuffer(backend.gpuContext, config.width, config.height),
                        val renderSystems: MutableList<RenderSystem> = CopyOnWriteArrayList(),
                        val renderStateManager: RenderStateManager = RenderStateManager { RenderState(backend.gpuContext) },
                        val materialManager: MaterialManager = MaterialManager(config, backend.eventBus, backend.textureManager, backend.addResourceContext)): Backend<OpenGl> {
    override val eventBus
        get() = backend.eventBus
    override val gpuContext: GpuContext<OpenGl>
        get() = backend.gpuContext
    override val programManager: ProgramManager<OpenGl>
        get() = backend.programManager
    override val textureManager: TextureManager
        get() = backend.textureManager
    override val input: Input
        get() = backend.input
    override val addResourceContext: AddResourceContext
        get() = backend.addResourceContext
}

class ManagerContextImpl(
        override val engineContext: EngineContext,
        override val managers: ManagerRegistry = SimpleManagerRegistry(),
        override val renderManager: RenderManager,
        override val physicsManager: PhysicsManager = PhysicsManager(renderer = LineRendererImpl(engineContext), config = engineContext.config)
) : ManagerContext<OpenGl> {

    override val directories = engineContext.config.directories

    init {
        managers.register(directories)
        managers.register(renderManager)
        managers.register(physicsManager)
        engineContext.renderSystems.add(physicsManager)
    }

    override fun beforeSetScene(nextScene: Scene) {
        for (manager in managers.managers) {
            manager.value.beforeSetScene(nextScene)
        }
    }

    override fun afterSetScene() {
        for (manager in managers.managers) {
            manager.value.afterSetScene()
        }
    }
}
