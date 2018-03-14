package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.event.DirectionalLightHasMovedEvent
import de.hanno.hpengine.engine.event.SceneInitEvent
import de.hanno.hpengine.engine.manager.Manager

class SceneManager(val engine: Engine): Manager {

    var scene: Scene = Scene(engine = engine)
        set(value) {
            onSetScene(value)
            engine.commandQueue.execute({
                field = value
            }, true)
            engine.eventBus.post(SceneInitEvent())
        }
    var activeCamera: Camera = scene.activeCamera.getComponent(Camera::class.java)

    override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
        val newDrawCycle = engine.renderManager.drawCycle.get()
        scene.currentCycle = newDrawCycle
        scene.update(deltaSeconds)


//        if (scene.entityMovedInCycle() == newDrawCycle) {
//            engine.eventBus.post(EntityMovedEvent()) TODO: Check if this is still necessary
//        }
        if (scene.directionalLightSystem.getDirectionalLight().entity.hasMoved()) {
            engine.eventBus.post(DirectionalLightHasMovedEvent())
        }
    }

    private fun onSetScene(nextScene: Scene) {
        engine.sceneManager.scene.entityManager.clear()
        engine.sceneManager.scene.environmentProbeManager.clearProbes()
        engine.physicsManager.clearWorld()
        engine.renderManager.clear()
        engine.getScene().modelComponentSystem.clear()
        nextScene.init(engine)
        activeCamera = nextScene.camera.getComponent(Camera::class.java)
        engine.renderManager.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(engine.renderManager.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(engine.renderManager.vertexIndexBufferAnimated)
        }
        nextScene.entitySystems.gatherEntities()
    }

    fun restoreWorldCamera() {
        activeCamera = scene.camera.getComponent(Camera::class.java)
    }

    init {
        scene.init(engine)
    }

    override fun clear() {
    }
}