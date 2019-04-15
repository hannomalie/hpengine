package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.BackendType
import de.hanno.hpengine.engine.backend.ManagerContext
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.manager.Manager

private class TempEngineImpl<TYPE: BackendType>(override val managerContext: ManagerContext<TYPE>, override val sceneManager: SceneManager): Engine<TYPE>, ManagerContext<TYPE> by managerContext
class SceneManager(val managerContext: ManagerContext<OpenGl>): Manager {

    var scene: Scene = SimpleScene("InitScene", TempEngineImpl(managerContext, this@SceneManager))
        set(value) {
            onSetScene(value)
            managerContext.commandQueue.execute(Runnable {
                field = value
            }, true)
            managerContext.eventBus.post(SceneInitEvent())
        }

    override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
        val newDrawCycle = managerContext.renderManager.drawCycle.get()
        scene.currentCycle = newDrawCycle
        scene.update(deltaSeconds)

    }

    private fun onSetScene(nextScene: Scene) {
        scene.environmentProbeManager.clearProbes()
        managerContext.physicsManager.clearWorld()
        managerContext.renderManager.clear()
        scene.clear()
//        TODO: Do we need this anymore?
//        nextScene.init(managerContext)
        managerContext.renderManager.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(managerContext.renderManager.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(managerContext.renderManager.vertexIndexBufferAnimated)
        }
        nextScene.entitySystems.gatherEntities()
    }

    init {
//        TODO: Do we need this anymore?
//        scene.init(managerContext)
    }

    override fun clear() {
    }
}