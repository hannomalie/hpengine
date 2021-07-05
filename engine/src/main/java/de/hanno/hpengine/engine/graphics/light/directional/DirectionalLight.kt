package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.InputControllerComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.scene.Scene
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

data class DirectionalLight(val _entity: Entity) : Camera(_entity, 1f) {
    private val castsShadows = false
    var color = Vector3f(1f, 1f, 1f)
    var scatterFactor = 1f

    val direction: Vector3f
        get() = _entity.transform.viewDirection

    fun translate(offset: Vector3f?) {
        _entity.transform.translate(offset)
    }

    data class DirectionalLightController(private val engine: EngineContext, private val _entity: Entity) : InputControllerComponent(_entity) {
        override suspend fun update(scene: Scene, deltaSeconds: Float) {
            val moveAmount = 100 * deltaSeconds
            val degreesPerSecond = 45f
            val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * deltaSeconds
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                entity.transform.rotateAround(Vector3f(0f, 1f, 0f), rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                entity.transform.rotateAround(Vector3f(0f, 1f, 0f), -rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
                entity.transform.rotateAround(Vector3f(1f, 0f, 0f), rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
                entity.transform.rotateAround(Vector3f(1f, 0f, 0f), -rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_8)) {
                entity.transform.translate(Vector3f(0f, -moveAmount, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_2)) {
                entity.transform.translate(Vector3f(0f, moveAmount, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_4)) {
                entity.transform.translate(Vector3f(-moveAmount, 0f, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_6)) {
                entity.transform.translate(Vector3f(moveAmount, 0f, 0f))
            }
        }
    }

    init {
        perspective = false
        color = Vector3f(1f, 0.76f, 0.49f)
        scatterFactor = 1f
        width = 1500f
        height = 1500f
        far = (-5000).toFloat()
        _entity.transform.translate(Vector3f(12f, 300f, 2f))
        _entity.transform.rotateAroundLocal(Quaternionf(AxisAngle4f(Math.toRadians(100.0).toFloat(), 1f, 0f, 0f)), 0f, 0f, 0f)
    }
}