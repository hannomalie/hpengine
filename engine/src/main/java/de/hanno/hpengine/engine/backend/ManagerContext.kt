package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.directory.DirectoryManager
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.util.commandqueue.CommandQueue

interface ManagerContext<TYPE: BackendType>: EngineContext<TYPE> {
    val engineContext: EngineContext<TYPE>
    val managers: ManagerRegistry
    val directoryManager: DirectoryManager
    val renderManager: RenderManager
    val physicsManager: PhysicsManager

    override val backend: Backend<TYPE>
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