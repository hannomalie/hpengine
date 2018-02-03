package de.hanno.hpengine.engine.scene

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.camera.MovableCamera
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext
import de.hanno.hpengine.util.stopwatch.StopWatch
import org.joml.Vector3f

class SceneManager {
    val camera = MovableCamera()
    var activeCamera: Camera = camera

    var scene: Scene = Scene()
        set(value) {
            onSetScene(value)
            field = value
        }

    private fun onSetScene(value: Scene) {
        Engine.getInstance().environmentProbeFactory.clearProbes()
        Engine.getInstance().physicsFactory.clearWorld()
        Engine.getInstance().renderSystem.resetAllocations()
        value.init()
        restoreWorldCamera()
        Engine.getInstance().renderSystem.renderState.addCommand { renderState1 ->
            renderState1.setVertexIndexBufferStatic(Engine.getInstance().renderSystem.vertexIndexBufferStatic)
            renderState1.setVertexIndexBufferAnimated(Engine.getInstance().renderSystem.vertexIndexBufferAnimated)
        }
    }


    init {
        camera.initialize()
        camera.setTranslation(Vector3f(0f, 20f, 0f))
        activeCamera = camera
        scene.init()
    }

    fun restoreWorldCamera() {
        activeCamera = camera
    }
}