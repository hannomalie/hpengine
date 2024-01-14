package de.hanno.hpengine.graphics.editor

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.managers.TagManager
import de.hanno.hpengine.component.CameraComponent
import de.hanno.hpengine.component.TransformComponent
import de.hanno.hpengine.component.primaryCameraTag
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.system.Extractor
import net.mostlyoriginal.api.Singleton
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.annotation.Single
import org.lwjgl.glfw.GLFW
import kotlin.math.max

@Singleton
class EditorCameraInputComponent: Component() {
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

    var prioritizeGameInput = false
}

@Single(binds = [BaseSystem::class, EditorCameraInputSystem::class])
class EditorCameraInputSystem(
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val input: Input,
): BaseSystem(), Extractor {

    lateinit var editorCameraInputComponent: EditorCameraInputComponent
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var cameraComponentMapper: ComponentMapper<CameraComponent>
    lateinit var tagManager: TagManager

    override fun processSystem() {
        if(!tagManager.isRegistered(primaryCameraTag)) return

        val entityId = tagManager.getEntity(primaryCameraTag)
        val transform = transformComponentMapper[entityId].transform
        val deltaSeconds =  world.delta

        editorCameraInputComponent.run {
            var turbo = 1f

            if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                turbo = 3f
            }

            val rotationAmount = 10.1f * turbo * deltaSeconds * rotationDelta * cameraSpeed
            if (input.isMousePressed(0)) {
                val pitchAmount = Math.toRadians((input.dySmooth * rotationAmount % 360).toDouble())
                pitchAccel = max(2 * Math.PI, pitchAccel + pitchAmount).toFloat()
                pitchAccel = max(0f, pitchAccel * 0.9f)

                val yawAmount = Math.toRadians((input.dxSmooth * rotationAmount % 360).toDouble())
                yawAccel = max(2 * Math.PI, yawAccel + yawAmount).toFloat()
                yawAccel = max(0f, yawAccel * 0.9f)

                yaw += yawAmount.toFloat()
                pitch += pitchAmount.toFloat()

                val oldTranslation = transform.getTranslation(Vector3f())
                transform.setTranslation(Vector3f(0f, 0f, 0f))
                transform.rotateLocalY((-yawAmount).toFloat())
                transform.rotateX(pitchAmount.toFloat())
                transform.translateLocal(oldTranslation)
            }

            val moveAmount = turbo * posDelta * deltaSeconds * cameraSpeed
            if (input.isKeyPressed(GLFW.GLFW_KEY_W)) {
                transform.translate(Vector3f(0f, 0f, -moveAmount))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_S)) {
                transform.translate(Vector3f(0f, 0f, moveAmount))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_A)) {
                transform.translate(Vector3f(-moveAmount, 0f, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_D)) {
                transform.translate(Vector3f(moveAmount, 0f, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_Q)) {
                transform.translate(Vector3f(0f, -moveAmount, 0f))
            }
            if (input.isKeyPressed(GLFW.GLFW_KEY_E)) {
                transform.translate(Vector3f(0f, moveAmount, 0f))
            }
        }
    }
    override fun extract(currentWriteState: RenderState) {
        if(!tagManager.isRegistered(primaryCameraTag)) return

        val entityId = tagManager.getEntity(primaryCameraTag)
        val transform = transformComponentMapper[entityId].transform
        val camera = cameraComponentMapper[entityId].camera
        val cameraState = currentWriteState[primaryCameraStateHolder.camera]
        camera.transform.set(transform)
        cameraState.setFrom(camera)
    }
}