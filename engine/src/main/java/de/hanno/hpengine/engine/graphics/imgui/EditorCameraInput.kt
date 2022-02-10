package de.hanno.hpengine.engine.graphics.imgui

import de.hanno.hpengine.engine.component.BaseComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.extension.CameraExtension
import de.hanno.hpengine.engine.extension.Extension
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.SimpleComponentSystem
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.dsl.*
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.get
import org.lwjgl.glfw.GLFW

class EditorExtension: Extension {
    override fun SceneDescription.decorate() {
        entity("EditorCamera") {
            add(EditorCameraInputComponentDescription())
            add(CameraDescription())
        }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        scene.getEntity("EditorCamera")?.let {
            scene.getKoin().get<CameraExtension>().run {
                scene.activeCameraEntity = it
            }
        }
    }
}
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

class EditorCameraInputSystem: SimpleComponentSystem<EditorCameraInputComponent>(EditorCameraInputComponent::class.java) {
    var cameraControlsEnabled = false
    override suspend fun update(scene: Scene, deltaSeconds: Float) {
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