package de.hanno.hpengine.engine.backend

import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.graphics.RenderManager
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.ManagerRegistry
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.physics.PhysicsManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.commandqueue.CommandQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

interface ManagerContext<TYPE: BackendType>: EngineContext<TYPE> {
    val engineContext: EngineContext<TYPE>
    val managers: ManagerRegistry
    val directories: Directories
    val renderManager: RenderManager
    val physicsManager: PhysicsManager

    fun onSetScene(nextScene: Scene)

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
    override val deferredRenderingBuffer: DeferredRenderingBuffer
        get() = engineContext.deferredRenderingBuffer
    override val materialManager: MaterialManager
        get() = engineContext.materialManager
    override val window: Window<TYPE>
        get() = engineContext.window
    override val singleThreadUpdateScope: CoroutineDispatcher
        get() = engineContext.singleThreadUpdateScope
}