package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.component.InputControllerComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.ComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import kotlinx.coroutines.CoroutineScope
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

class MovableInputComponent(val engine: EngineContext, override val entity: Entity) : InputControllerComponent(entity) {

    protected var rotationDelta = 10f
    protected var scaleDelta = 0.1f
    protected var posDelta = 100f

    var linearAcc = Vector3f()
    var linearVel = Vector3f()

    /** Always rotation about the local XYZ axes of the camera!  */
    var angularAcc = Vector3f()
    var angularVel = Vector3f()

    var position = Vector3f(0f, 0f, 10f)
    var rotation = Quaternionf()

    private var pitch = 0f
    private var yaw = 0f

    private var pitchAccel = 0f
    private var yawAccel = 0f


//    TODO: Make this adjustable through editor
    private val cameraSpeed: Float = 1.0f

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        //                             linearVel.fma(deltaSeconds, linearAcc);
        //                             // update angular velocity based on angular acceleration
        //                             angularVel.fma(deltaSeconds, angularAcc);
        //                             // update the rotation based on the angular velocity
        //                             rotation.integrate(deltaSeconds, angularVel.x, angularVel.y, angularVel.z);
        //                             // update position based on linear velocity
        //                             position.fma(deltaSeconds, linearVel);

        var turbo = 1f
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            turbo = 3f
        }

        val rotationAmount = 10.1f * turbo * deltaSeconds * this@MovableInputComponent.rotationDelta * this@MovableInputComponent.cameraSpeed
        if (this@MovableInputComponent.engine.input.isMouseClicked(0)) {
            val pitchAmount = Math.toRadians((this@MovableInputComponent.engine.input.dySmooth * rotationAmount % 360).toDouble())
            this@MovableInputComponent.pitchAccel = Math.max(2 * Math.PI, this@MovableInputComponent.pitchAccel + pitchAmount).toFloat()
            this@MovableInputComponent.pitchAccel = Math.max(0f, this@MovableInputComponent.pitchAccel * 0.9f)

            val yawAmount = Math.toRadians((this@MovableInputComponent.engine.input.dxSmooth * rotationAmount % 360).toDouble())
            this@MovableInputComponent.yawAccel = Math.max(2 * Math.PI, this@MovableInputComponent.yawAccel + yawAmount).toFloat()
            this@MovableInputComponent.yawAccel = Math.max(0f, this@MovableInputComponent.yawAccel * 0.9f)

            this@MovableInputComponent.yaw += yawAmount.toFloat()
            this@MovableInputComponent.pitch += pitchAmount.toFloat()

            val oldTranslation = this@MovableInputComponent.entity.transform.getTranslation(Vector3f())
            this@MovableInputComponent.entity.transform.setTranslation(Vector3f(0f,0f,0f))
            this@MovableInputComponent.entity.transform.rotateLocalY((-yawAmount).toFloat())
            this@MovableInputComponent.entity.transform.rotateX(pitchAmount.toFloat())
            this@MovableInputComponent.entity.transform.translateLocal(oldTranslation)
        }

        val moveAmount = turbo * this@MovableInputComponent.posDelta * deltaSeconds * this@MovableInputComponent.cameraSpeed
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_W)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_S)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_A)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_D)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_Q)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (this@MovableInputComponent.engine.input.isKeyPressed(GLFW_KEY_E)) {
            this@MovableInputComponent.entity.transform.translate(Vector3f(0f, moveAmount, 0f))
        }

    }

    companion object {
        private val serialVersionUID = 1L
    }
}

class InputComponentSystem(val engine: EngineContext): ComponentSystem<InputControllerComponent> {
    override val componentClass: Class<InputControllerComponent> = InputControllerComponent::class.java
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        getComponents().forEach {
            it.update(scene, deltaSeconds)
        }
    }
    private val components = mutableListOf<InputControllerComponent>()
    override fun getComponents(): List<InputControllerComponent> = components

    override fun addComponent(component: InputControllerComponent) {
        components.add(component)
    }
    override fun clear() = components.clear()
}