package de.hanno.hpengine.engine.graphics.light.directional

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.InputControllerComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.shader.Program
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW

class DirectionalLight(entity: Entity) : Camera(entity, 1f) {
    private val castsShadows = false
    var color = Vector3f(1f, 1f, 1f)
    var scatterFactor = 1f

    fun drawDebug(program: Program?) {
        throw IllegalStateException("Currently not implemented!")
        //		box.getComponentOption(ModelComponent.class).ifPresent(modelComponent -> {
//			modelComponent.drawDebug(program, getTransform().getTransformationBuffer());
//		});
    }

    val direction: Vector3f
        get() = entity.viewDirection

    fun translate(offset: Vector3f?) {
        entity.translate(offset)
    }

    class DirectionalLightController(private val engine: EngineContext<*>, entity: Entity) : InputControllerComponent(entity) {
        override fun CoroutineScope.update(deltaSeconds: Float) {
            val moveAmount = 100 * deltaSeconds
            val degreesPerSecond = 45f
            val rotateAmount = Math.toRadians(degreesPerSecond.toDouble()).toFloat() * deltaSeconds
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_UP)) {
                entity.rotateAround(Vector3f(0f, 1f, 0f), rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_DOWN)) {
                entity.rotateAround(Vector3f(0f, 1f, 0f), -rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_LEFT)) {
                entity.rotateAround(Vector3f(1f, 0f, 0f), rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_RIGHT)) {
                entity.rotateAround(Vector3f(1f, 0f, 0f), -rotateAmount, Vector3f())
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_8)) {
                entity.translate(Vector3f(0f, -moveAmount, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_2)) {
                entity.translate(Vector3f(0f, moveAmount, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_4)) {
                entity.translate(Vector3f(-moveAmount, 0f, 0f))
            }
            if (engine.input.isKeyPressed(GLFW.GLFW_KEY_6)) {
                entity.translate(Vector3f(moveAmount, 0f, 0f))
            }
        }

        companion object {
            private const val serialVersionUID = 1L
        }

    }

    init {
        perspective = false
        color = Vector3f(1f, 0.76f, 0.49f)
        scatterFactor = 1f
        width = 1500f
        height = 1500f
        far = (-5000).toFloat()
        entity.rotateAround(Vector3f(1f, 0f, 0f), Math.toRadians(100.0).toFloat(), Vector3f())
        entity.translate(Vector3f(12f, 300f, 2f))
    }
}