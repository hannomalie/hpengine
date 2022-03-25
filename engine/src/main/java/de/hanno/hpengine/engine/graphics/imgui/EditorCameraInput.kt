package de.hanno.hpengine.engine.graphics.imgui

import com.artemis.BaseSystem
import com.artemis.Component
import com.artemis.ComponentMapper
import com.artemis.annotations.Wire
import com.artemis.managers.TagManager
import de.hanno.hpengine.engine.component.BaseComponent
import de.hanno.hpengine.engine.component.artemis.CameraComponent
import de.hanno.hpengine.engine.component.artemis.TransformComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.dsl.*
import net.mostlyoriginal.api.Singleton
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.get
import org.lwjgl.glfw.GLFW


class EditorCameraInputComponentDescription: ComponentDescription
class EditorCameraInputComponent(override val entity: Entity): BaseComponent(entity) {
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
@Singleton
class EditorCameraInputComponentNew: Component() {
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

class EditorCameraInputSystem: SimpleComponentSystem<EditorCameraInputComponent>(EditorCameraInputComponent::class.java) {
    var cameraControlsEnabled = false
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        return

        val input = scene.get<Input>()
        if(input.wasKeyReleasedLastFrame(GLFW.GLFW_KEY_T) && input.isKeyPressed(GLFW.GLFW_KEY_T)) {
            cameraControlsEnabled = !cameraControlsEnabled
        }

        if(!cameraControlsEnabled) return

        components.forEach {
            with(it) {
                var turbo = 1f

                if (input.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT)) {
                    turbo = 3f
                }

                val rotationAmount = 10.1f * turbo * deltaSeconds * rotationDelta * cameraSpeed
                if (input.isMouseClicked(0)) {
                    val pitchAmount = Math.toRadians((input.dySmooth * rotationAmount % 360).toDouble())
                    pitchAccel = Math.max(2 * Math.PI, pitchAccel + pitchAmount).toFloat()
                    pitchAccel = Math.max(0f, pitchAccel * 0.9f)

                    val yawAmount = Math.toRadians((input.dxSmooth * rotationAmount % 360).toDouble())
                    yawAccel = Math.max(2 * Math.PI, yawAccel + yawAmount).toFloat()
                    yawAccel = Math.max(0f, yawAccel * 0.9f)

                    yaw += yawAmount.toFloat()
                    pitch += pitchAmount.toFloat()

                    val oldTranslation = entity.transform.getTranslation(Vector3f())
                    entity.transform.setTranslation(Vector3f(0f, 0f, 0f))
                    entity.transform.rotateLocalY((-yawAmount).toFloat())
                    entity.transform.rotateX(pitchAmount.toFloat())
                    entity.transform.translateLocal(oldTranslation)
                }

                val moveAmount = turbo * posDelta * deltaSeconds * cameraSpeed
                if (input.isKeyPressed(GLFW.GLFW_KEY_W)) {
                    entity.transform.translate(Vector3f(0f, 0f, -moveAmount))
                }
                if (input.isKeyPressed(GLFW.GLFW_KEY_S)) {
                    entity.transform.translate(Vector3f(0f, 0f, moveAmount))
                }
                if (input.isKeyPressed(GLFW.GLFW_KEY_A)) {
                    entity.transform.translate(Vector3f(-moveAmount, 0f, 0f))
                }
                if (input.isKeyPressed(GLFW.GLFW_KEY_D)) {
                    entity.transform.translate(Vector3f(moveAmount, 0f, 0f))
                }
                if (input.isKeyPressed(GLFW.GLFW_KEY_Q)) {
                    entity.transform.translate(Vector3f(0f, -moveAmount, 0f))
                }
                if (input.isKeyPressed(GLFW.GLFW_KEY_E)) {
                    entity.transform.translate(Vector3f(0f, moveAmount, 0f))
                }
            }
        }
    }
}

const val primaryCamera = "PRIMARY_CAMERA"
class EditorCameraInputSystemNew: BaseSystem() {

    lateinit var editorCameraInputComponent: EditorCameraInputComponentNew
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
                if (input.isMouseClicked(0)) {
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
    fun extract(renderState: RenderState) {
        if(!tagManager.isRegistered(primaryCamera)) return

        val entityId = tagManager.getEntity(primaryCamera)
        val transform = transformComponentMapper[entityId].transform
        val camera = cameraComponentMapper[entityId]
        renderState.camera.entity.transform.set(transform)
        renderState.camera.init(
            camera.projectionMatrix, camera.near, camera.far, camera.fov, camera.ratio,
            camera.exposure, camera.focalDepth, camera.focalLength, camera.fStop
        )
    }
}