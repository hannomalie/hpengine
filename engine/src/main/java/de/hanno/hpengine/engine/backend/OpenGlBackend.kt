package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.directory.DirectoryManager
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.event.bus.EventBus
import de.hanno.hpengine.engine.event.bus.MBassadorEventBus
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.OpenGLContext
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.shader.OpenGlProgramManager
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

interface OpenGl: BackendType {
    val gpuContext: OpenGLContext
}

class OpenGlBackend(override val eventBus: EventBus,
                    override val gpuContext: GpuContext<OpenGl>,
                    override val programManager: ProgramManager<OpenGl>,
                    override val textureManager: TextureManager,
                    override val input: Input) : Backend<OpenGl> {

    companion object {
        operator fun invoke(): OpenGlBackend {
            val eventBus= MBassadorEventBus()
            val gpuContext = OpenGLContext()
            val programManager = OpenGlProgramManager(gpuContext, eventBus)
            val textureManager = TextureManager(programManager, gpuContext)
            val input = Input(eventBus, gpuContext)

            return OpenGlBackend(eventBus, gpuContext, programManager, textureManager, input)
        }
    }
}

class UpdateCommandQueue: CommandQueue(Executors.newSingleThreadExecutor(), { UpdateThread.isUpdateThread() })

class EngineContextImpl(override val commandQueue: CommandQueue = UpdateCommandQueue(),
                        override val backend: Backend<OpenGl> = OpenGlBackend(),
                        override val config: Config = Config.getInstance(),
                        override val renderSystems: MutableList<RenderSystem> = CopyOnWriteArrayList(),
                        override val renderStateManager: RenderStateManager = RenderStateManager { RenderState(backend.gpuContext) }) : EngineContext<OpenGl>

class ManagerContextImpl(
        override val engineContext: EngineContext<OpenGl>,
        override val managers: ManagerRegistry = SimpleManagerRegistry(),
        override val directoryManager: DirectoryManager = DirectoryManager(gameDir = engineContext.config.gameDir, initFileName = engineContext.config.initFileName),
        override val renderManager: RenderManager,
        override val physicsManager: PhysicsManager = PhysicsManager(renderManager.renderer)
) : ManagerContext<OpenGl> {
    init {
        managers.register(directoryManager)
        managers.register(renderManager)
        managers.register(physicsManager)
        engineContext.renderSystems.add(physicsManager)
    }
}