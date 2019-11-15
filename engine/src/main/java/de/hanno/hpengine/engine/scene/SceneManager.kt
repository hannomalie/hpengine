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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class TempEngineImpl<TYPE: BackendType>(override val managerContext: ManagerContext<TYPE>,
                                                override val sceneManager: SceneManager): Engine<TYPE>, ManagerContext<TYPE> by managerContext {
    override val singleThreadContext: SingleThreadContext = SingleThreadContext()
}

class SingleThreadContext(val singleThreadUpdateScope: ExecutorCoroutineDispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()) {
    init {
        GlobalScope.async {}
    }
    fun launch(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend SingleThreadContext.() -> Unit
    ): Job {
        return GlobalScope.launch(singleThreadUpdateScope, start) { block() }
    }

    fun <T> CoroutineScope.async(
            start: CoroutineStart = CoroutineStart.DEFAULT,
            block: suspend SingleThreadContext.() -> T
    ): Deferred<T> {
        return GlobalScope.async(singleThreadUpdateScope, start) { block() }
    }

    @Throws(InterruptedException::class)
    fun <T> runBlocking(block: SingleThreadContext.() -> T): T {
        return kotlinx.coroutines.runBlocking(singleThreadUpdateScope) {
            with(this@SingleThreadContext) {
                block()
            }
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