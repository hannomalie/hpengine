package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.event.EntityAddedEvent
import de.hanno.hpengine.engine.event.MaterialChangedEvent
import de.hanno.hpengine.engine.manager.Manager

class SceneManager(val engine: Engine): Manager {

    var scene: Scene = Scene(engine = engine, sceneManager = this)
        set(value) {
            onSetScene(value)
            engine.commandQueue.execute({
//                TODO: Make this possible with moving systems to scene
//                engine.systems.clearSystems()
                field = value
            }, true)
        }
    var activeCamera: Camera = scene.activeCamera.getComponent(Camera::class.java)

    override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
//        activeCamera.update(engine, deltaSeconds)
        scene.currentCycle = engine.renderManager.drawCycle.get()
        scene.update(engine, deltaSeconds)
    }

    private fun onSetScene(nextScene: Scene) {
        engine.sceneManager.scene.entityManager.clear()
        engine.sceneManager.scene.environmentProbeManager.clearProbes()
        engine.physicsManager.clearWorld()
        engine.renderManager.clear()
        engine.getScene().modelComponentSystem.clear()
        nextScene.init(engine)
        restoreWorldCamera()
        activeCamera = nextScene.camera.getComponent(Camera::class.java)
        engine.renderManager.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(engine.renderManager.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(engine.renderManager.vertexIndexBufferAnimated)
        }
        nextScene.entitySystems.gatherEntities()
    }


    init {
//        camera.initialize()
//        camera.setTranslation(Vector3f(0f, 20f, 0f))
//        activeCamera = camera
        scene.init(engine)
    }

    fun restoreWorldCamera() {
//        activeCamera = camera
    }
    override fun clear() {
    }
}