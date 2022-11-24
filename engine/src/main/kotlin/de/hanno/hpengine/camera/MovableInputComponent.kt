package de.hanno.hpengine.camera

import com.artemis.BaseEntitySystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.All
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.artemis.forEachEntity
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.input.Input
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

@All(MovableInputComponent::class, TransformComponent::class)
class MovableInputComponentComponentSystem: BaseEntitySystem() {
    lateinit var input: Input
    lateinit var config: Config

    lateinit var movableInputComponentComponentMapper: ComponentMapper<MovableInputComponent>
    lateinit var transformComponentComponentMapper: ComponentMapper<TransformComponent>

    override fun processSystem() {
        if(config.debug.isEditorOverlay) return
        val deltaSeconds = world.delta

        forEachEntity { entityId ->
            val inputComponent = movableInputComponentComponentMapper[entityId]
            val transform = transformComponentComponentMapper[entityId].transform

            with(inputComponent) {

//                             linearVel.fma(deltaSeconds, linearAcc);
//                             // update angular velocity based on angular acceleration
//                             angularVel.fma(deltaSeconds, angularAcc);
//                             // update the rotation based on the angular velocity
//                             rotation.integrate(deltaSeconds, angularVel.x, angularVel.y, angularVel.z);
//                             // update position based on linear velocity
//                             position.fma(deltaSeconds, linearVel);

                var turbo = 1f

                if (input.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
                    turbo = 3f
                }

                val rotationAmount = 10.1f * turbo * deltaSeconds * rotationDelta * cameraSpeed
                if (input.isMousePressed(0)) {
                    val pitchAmount = Math.toRadians((input.dySmooth * rotationAmount % 360).toDouble())
                    pitchAccel = Math.max(2 * Math.PI, pitchAccel + pitchAmount).toFloat()
                    pitchAccel = Math.max(0f, pitchAccel * 0.9f)

                    val yawAmount = Math.toRadians((input.dxSmooth * rotationAmount % 360).toDouble())
                    yawAccel = Math.max(2 * Math.PI, yawAccel + yawAmount).toFloat()
                    yawAccel = Math.max(0f, yawAccel * 0.9f)

                    yaw += yawAmount.toFloat()
                    pitch += pitchAmount.toFloat()

                    val oldTranslation = transform.getTranslation(Vector3f())
                    transform.setTranslation(Vector3f(0f,0f,0f))
                    transform.rotateLocalY((-yawAmount).toFloat())
                    transform.rotateX(pitchAmount.toFloat())
                    transform.translateLocal(oldTranslation)
                }

                val moveAmount = turbo * posDelta * deltaSeconds * cameraSpeed
                if (input.isKeyPressed(GLFW_KEY_W)) {
                    transform.translate(Vector3f(0f, 0f, -moveAmount))
                }
                if (input.isKeyPressed(GLFW_KEY_S)) {
                    transform.translate(Vector3f(0f, 0f, moveAmount))
                }
                if (input.isKeyPressed(GLFW_KEY_A)) {
                    transform.translate(Vector3f(-moveAmount, 0f, 0f))
                }
                if (input.isKeyPressed(GLFW_KEY_D)) {
                    transform.translate(Vector3f(moveAmount, 0f, 0f))
                }
                if (input.isKeyPressed(GLFW_KEY_Q)) {
                    transform.translate(Vector3f(0f, -moveAmount, 0f))
                }
                if (input.isKeyPressed(GLFW_KEY_E)) {
                    transform.translate(Vector3f(0f, moveAmount, 0f))
                }

            }
        }
    }
}

class MovableInputComponent : Component(){
    var rotationDelta = 10f
    var scaleDelta = 0.1f
    var posDelta = 100f

    var linearAcc = Vector3f()
    var linearVel = Vector3f()

    /** Always rotation about the local XYZ axes of the camera!  */
    var angularAcc = Vector3f()
    var angularVel = Vector3f()

    var position = Vector3f(0f, 0f, 10f)
    var rotation = Quaternionf()

    var pitch = 0f
    var yaw = 0f

    var pitchAccel = 0f
    var yawAccel = 0f


//    TODO: Make this adjustable through editor
    val cameraSpeed: Float = 1.0f

}
