package de.hanno.hpengine.engine.camera

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.InputControllerComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.ComponentSystem
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

class MovableInputComponent(val engine: EngineContext<*>, entity: Entity) : InputControllerComponent(entity) {

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

    init {
        this.entity = entity
    }

//    TODO: Make this adjustable through editor
    private val cameraSpeed: Float = 1.0f

    override fun update(seconds: Float) {
        //                             linearVel.fma(seconds, linearAcc);
        //                             // update angular velocity based on angular acceleration
        //                             angularVel.fma(seconds, angularAcc);
        //                             // update the rotation based on the angular velocity
        //                             rotation.integrate(seconds, angularVel.x, angularVel.y, angularVel.z);
        //                             // update position based on linear velocity
        //                             position.fma(seconds, linearVel);

        var turbo = 1f
        if (engine.input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            turbo = 3f
        }

        val rotationAmount = 10.1f * turbo * seconds * rotationDelta * cameraSpeed
        if (engine.input.isMouseClicked(0)) {
            val pitchAmount = Math.toRadians((engine.input.dySmooth * rotationAmount % 360).toDouble())
            pitchAccel = Math.max(2 * Math.PI, pitchAccel + pitchAmount).toFloat()
            pitchAccel = Math.max(0f, pitchAccel * 0.9f)

            val yawAmount = Math.toRadians((engine.input.dxSmooth * rotationAmount % 360).toDouble())
            yawAccel = Math.max(2 * Math.PI, yawAccel + yawAmount).toFloat()
            yawAccel = Math.max(0f, yawAccel * 0.9f)

            yaw += yawAmount.toFloat()
            pitch += pitchAmount.toFloat()

            val oldTranslation = getEntity().getTranslation(Vector3f())
            getEntity().setTranslation(Vector3f(0f,0f,0f))
            getEntity().rotateLocalY((-yawAmount).toFloat())
            getEntity().rotateX(pitchAmount.toFloat())
            getEntity().translateLocal(oldTranslation)
        }

        val moveAmount = turbo * posDelta * seconds * cameraSpeed
        if (engine.input.isKeyPressed(GLFW_KEY_W)) {
            getEntity().translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (engine.input.isKeyPressed(GLFW_KEY_S)) {
            getEntity().translate(Vector3f(0f, 0f, moveAmount))
        }
        if (engine.input.isKeyPressed(GLFW_KEY_A)) {
            getEntity().translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (engine.input.isKeyPressed(GLFW_KEY_D)) {
            getEntity().translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (engine.input.isKeyPressed(GLFW_KEY_Q)) {
            getEntity().translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (engine.input.isKeyPressed(GLFW_KEY_E)) {
            getEntity().translate(Vector3f(0f, moveAmount, 0f))
        }

    }

    companion object {
        private val serialVersionUID = 1L
    }
}

class InputComponentSystem(val engine: EngineContext<*>): ComponentSystem<InputControllerComponent> {
    override val componentClass: Class<InputControllerComponent> = InputControllerComponent::class.java
    override fun update(deltaSeconds: Float) { getComponents().forEach { it.update(deltaSeconds) } }
    private val components = mutableListOf<InputControllerComponent>()
    override fun getComponents(): List<InputControllerComponent> = components

    override fun create(entity: Entity) = MovableInputComponent(engine, entity).also { components.add(it); }
    override fun addComponent(component: InputControllerComponent) {
        components.add(component)
    }
    override fun clear() = components.clear()
}