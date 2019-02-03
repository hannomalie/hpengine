package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.DirectoryManager
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.manager.SimpleManagerRegistry
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.threads.UpdateThread
import de.hanno.hpengine.util.commandqueue.CommandQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

interface Backend {
    val eventBus: EventBus
    val gpuContext: GpuContext
    val programManager: ProgramManager
    val textureManager: TextureManager
    val input: Input
}

class BackendImpl(override val eventBus: EventBus = MBassadorEventBus(),
                  override val gpuContext: GpuContext = OpenGLContext(),
                  override val programManager: ProgramManager = ProgramManager(gpuContext, eventBus),
                  override val textureManager: TextureManager = TextureManager(programManager, gpuContext),
                  override val input: Input = Input(eventBus, gpuContext)) : Backend

interface EngineContext: Backend {
    val backend: Backend
    val commandQueue: CommandQueue
    val config: Config
    val renderSystems: MutableList<RenderSystem>
    val renderStateManager: RenderStateManager

    override val eventBus
        get() = backend.eventBus
    override val gpuContext: GpuContext
        get() = backend.gpuContext
    override val programManager: ProgramManager
        get() = backend.programManager
    override val textureManager: TextureManager
        get() = backend.textureManager
    override val input: Input
        get() = backend.input
}

class UpdateCommandQueue: CommandQueue(Executors.newSingleThreadExecutor(), { UpdateThread.isUpdateThread() })

class EngineContextImpl(override val commandQueue: CommandQueue = UpdateCommandQueue(),
                        override val backend: Backend = BackendImpl(),
                        override val config: Config = Config.getInstance(),
                        override val renderSystems: MutableList<RenderSystem> = CopyOnWriteArrayList(),
                        override val renderStateManager: RenderStateManager = RenderStateManager { RenderState(backend.gpuContext) }) : EngineContext

interface ManagerContext: EngineContext {
    val engineContext: EngineContext
    val managers: ManagerRegistry
    val directoryManager: DirectoryManager
    val renderManager: RenderManager
    val physicsManager: PhysicsManager

    override val backend: Backend
        get() = engineContext.backend
    override val input: Input
        get() = engineContext.input
    override val commandQueue: CommandQueue
        get() = engineContext.commandQueue
    override val config: Config
        get() = engineContext.config
    override val renderSystems: MutableList<RenderSystem>
        get() = engineContext.renderSystems
    override val renderStateManager: RenderStateManager
        get() = engineContext.renderStateManager
}

class ManagerContextImpl(
        override val engineContext: EngineContext,
        override val managers: ManagerRegistry = SimpleManagerRegistry(),
        override val directoryManager: DirectoryManager = DirectoryManager(engineContext.config.gameDir),
        override val renderManager: RenderManager,
        override val physicsManager: PhysicsManager = PhysicsManager(renderManager.renderer)
) : ManagerContext {
    init {
        managers.register(directoryManager)
        managers.register(renderManager)
        managers.register(physicsManager)
        engineContext.renderSystems.add(physicsManager)
    }
}
