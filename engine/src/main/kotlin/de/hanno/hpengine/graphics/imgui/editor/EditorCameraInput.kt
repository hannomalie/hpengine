package de.hanno.hpengine.graphics.imgui.editor

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.artemis.managers.TagManager
import de.hanno.hpengine.artemis.CameraComponent
import de.hanno.hpengine.artemis.TransformComponent
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.input.Input
import de.hanno.hpengine.system.Extractor
import net.mostlyoriginal.api.Singleton
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW


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

    var cameraControlsEnabled = true
}

const val primaryCamera = "PRIMARY_CAMERA"
class EditorCameraInputSystem: BaseSystem(), Extractor {

    lateinit var editorCameraInputComponent: EditorCameraInputComponent
    lateinit var transformComponentMapper: ComponentMapper<TransformComponent>
    lateinit var cameraComponentMapper: ComponentMapper<CameraComponent>
    @Wire
    lateinit var input: Input
    lateinit var tagManager: TagManager

    override fun processSystem() {
        if(!tagManager.isRegistered(primaryCamera)) return

        val entityId = tagManager.getEntity(primaryCamera)
        val transform = transformComponentMapper[entityId].transform
        val deltaSeconds =  world.delta

        editorCameraInputComponent.run {

            if(input.wasKeyReleasedLastFrame(GLFW.GLFW_KEY_T) && input.isKeyPressed(GLFW.GLFW_KEY_T)) {
                cameraControlsEnabled = !cameraControlsEnabled
            }
            if(cameraControlsEnabled) {
                var turbo = 1f

                if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
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
    }
    override fun extract(currentWriteState: RenderState) {
        if(!tagManager.isRegistered(primaryCamera)) return

        val entityId = tagManager.getEntity(primaryCamera)
        val transform = transformComponentMapper[entityId].transform
        val camera = cameraComponentMapper[entityId]
        currentWriteState.camera.transform.set(transform)
        currentWriteState.camera.init(
            camera.projectionMatrix, camera.near, camera.far, camera.fov, camera.ratio,
            camera.exposure, camera.focalDepth, camera.focalLength, camera.fStop
        )
    }
}