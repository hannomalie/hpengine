package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private class TempEngineImpl<TYPE: BackendType>(override val managerContext: ManagerContext<TYPE>,
                                                override val sceneManager: SceneManager): Engine<TYPE>, ManagerContext<TYPE> by managerContext {
    override val singleThreadContext: SingleThreadContext = SingleThreadContext
}

private var singleThreadContextCounter = 0
object SingleThreadContext {
    val threadName: String = "SingleThreadContext${singleThreadContextCounter++}"
    private val singleThreadUpdateScope: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(1) { Thread(it, threadName) }.asCoroutineDispatcher()

    suspend fun <T> execute(block: () -> T): T = if(isSingleThreadContextThread) {
        block()
    } else {
        withContext(singleThreadUpdateScope) {
            block()
        }
    }

    fun launch(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend SingleThreadContext.() -> Unit
    ): Job {
        return GlobalScope.launch(start = start) {
            with(singleThreadUpdateScope) {
                block()
            }
        }
    }

    fun <T> CoroutineScope.async(
            start: CoroutineStart = CoroutineStart.DEFAULT,
            block: suspend SingleThreadContext.() -> T
    ): Deferred<T> {
        return GlobalScope.async(start = start) {
            with(singleThreadUpdateScope) {
                block()
            }
        }
    }

    private val isSingleThreadContextThread: Boolean
        get() = Thread.currentThread().name == threadName

    @Throws(InterruptedException::class)
    fun <T> runBlocking(block: SingleThreadContext.() -> T): T {
        if(isSingleThreadContextThread) {
            return block()
        }
        return kotlinx.coroutines.runBlocking(singleThreadUpdateScope) {
            block()
        }
    }
}

class SceneManager(val managerContext: ManagerContext<OpenGl>): Manager {

    var scene: Scene = SimpleScene("InitScene", TempEngineImpl(managerContext, this@SceneManager))
        set(value) {
            onSetScene(value)
            managerContext.commandQueue.execute(Runnable {
                field = value
            }, true)
            managerContext.eventBus.post(SceneInitEvent())
        }


    fun SingleThreadContext.addAll(entities: List<Entity>) {
        with(scene) { addAll(entities) }
        managerContext.managers.managers.values.forEach {
            with(it) { onEntityAdded(entities) }
        }
    }
    fun SingleThreadContext.add(entity: Entity) = addAll(listOf(entity))

    override fun CoroutineScope.update(deltaSeconds: Float) {
        val newDrawCycle = managerContext.renderManager.drawCycle.get()
        scene.currentCycle = newDrawCycle
        with(scene) {
            update(deltaSeconds)
        }
    }

    override fun onSetScene(nextScene: Scene) {
        scene.environmentProbeManager.clearProbes()
        managerContext.physicsManager.clearWorld()
        managerContext.renderManager.clear()
        scene.clear()
        managerContext.renderManager.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(managerContext.renderManager.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(managerContext.renderManager.vertexIndexBufferAnimated)
        }
        nextScene.entitySystems.gatherEntities()
    }

}