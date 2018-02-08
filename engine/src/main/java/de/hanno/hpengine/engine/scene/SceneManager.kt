package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableCamera
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.manager.Registry
import de.hanno.hpengine.engine.manager.SimpleRegistry
import org.joml.Vector3f

class SceneManager(override val engine: Engine): Manager {
//    val camera = MovableCamera()
//    var activeCamera: Camera = camera

    var scene: Scene = Scene(engine = engine)
        set(value) {
            onSetScene(value)
            field = value
        }
    var activeCamera: Camera = scene.activeCamera

    override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
//        activeCamera.update(engine, deltaSeconds)
        scene.setCurrentCycle(engine.renderManager.drawCycle.get())
        scene.update(engine, deltaSeconds)
    }

    private fun onSetScene(value: Scene) {
        engine.environmentProbeManager.clearProbes()
        engine.physicsManager.clearWorld()
        engine.renderManager.resetAllocations()
        value.init(engine)
        restoreWorldCamera()
        engine.renderManager.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(engine.renderManager.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(engine.renderManager.vertexIndexBufferAnimated)
        }
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
}