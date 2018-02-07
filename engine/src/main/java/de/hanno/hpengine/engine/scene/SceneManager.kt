package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableCamera
import de.hanno.hpengine.engine.manager.Manager
import org.joml.Vector3f

class SceneManager(override val engine: Engine): Manager {
    val camera = MovableCamera()
    var activeCamera: Camera = camera

    var scene: Scene = Scene()
        set(value) {
            onSetScene(value)
            field = value
        }

    override fun update(deltaSeconds: Float) {
        super.update(deltaSeconds)
        activeCamera.update(engine, deltaSeconds)
        scene.setCurrentCycle(engine.renderSystem.drawCycle.get())
        scene.update(engine, deltaSeconds)
    }

    private fun onSetScene(value: Scene) {
        engine.environmentProbeFactory.clearProbes()
        engine.physicsFactory.clearWorld()
        engine.renderSystem.resetAllocations()
        value.init(engine)
        restoreWorldCamera()
        engine.renderSystem.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(engine.renderSystem.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(engine.renderSystem.vertexIndexBufferAnimated)
        }
    }


    init {
        camera.initialize()
        camera.setTranslation(Vector3f(0f, 20f, 0f))
        activeCamera = camera
        scene.init(engine)
    }

    fun restoreWorldCamera() {
        activeCamera = camera
    }
}