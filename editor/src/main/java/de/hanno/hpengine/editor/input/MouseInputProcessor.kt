package de.hanno.hpengine.editor.input

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.selection.EntitySelection
import de.hanno.hpengine.editor.selection.Selection
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.graphics.renderer.extensions.xyz
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.camera
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.cameraEntity
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector4f
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.reflect.KProperty0

class MouseInputProcessor(val engineContext: EngineContext,
                          val selection: KProperty0<Selection>,
                          val editorComponents: EditorComponents) : MouseAdapter() {
    private var lastX: Float? = null
    private var lastY: Float? = null
    private val oldTransform = Matrix4f()

    private var pitch = 0f
    private var yaw = 0f

    override fun mousePressed(e: MouseEvent) {
        lastX = e.x.toFloat()
        lastY = e.y.toFloat()

        val entityOrNull = getEntityOrNull()
        entityOrNull?.transform?.transformation?.get(oldTransform)
    }

    private fun getEntityOrNull() = (selection.call() as? EntitySelection)?.entity

    override fun mouseReleased(e: MouseEvent) {
        val entityOrNull = getEntityOrNull()
        entityOrNull?.transform?.transformation?.get(oldTransform)
    }

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 2.0f * 0.05 * rotationDelta

        val deltaX = if(lastX != null) e.x.toFloat() - lastX!! else 0f
        val deltaY = if(lastY != null) -(e.y.toFloat() - lastY!!) else 0f

        val pitchAmount = Math.toRadians((deltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((deltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val entityOrNull = getEntityOrNull()
        if(entityOrNull == null) {
            val entity = editorComponents.sceneManager.scene.cameraEntity
            val oldTranslation = entity.transform.getTranslation(Vector3f())
            entity.transform.rotationX(pitchAmount.toFloat())
            entity.transform.rotateLocalY((-yawAmount).toFloat())
            entity.transform.translateLocal(oldTranslation)
        } else {
            val activeCamera = editorComponents.sceneManager.scene.activeCamera

            val viewPort = intArrayOf(0, 0, engineContext.window.width, engineContext.window.height)
            val entityPositionDevice = Vector4f()
            activeCamera.viewProjectionMatrix.project(entityOrNull.transform.position, viewPort, entityPositionDevice)
            val positionDeviceNew = entityPositionDevice.xyz.add(Vector3f(deltaX, deltaY, 0f))
            val entityNewWorldPosition = Vector4f()
            activeCamera.viewProjectionMatrix.unproject(positionDeviceNew, viewPort, entityNewWorldPosition)

            val constraintAxis = editorComponents.selectionSystem.axisDragged

            val movement = entityNewWorldPosition.xyz.sub(entityOrNull.transform.position)
            val axisConstrainedMovement = when(constraintAxis) {
                AxisConstraint.X -> Vector3f(movement.x, 0f, 0f)
                AxisConstraint.Y -> Vector3f(0f, movement.y, 0f)
                AxisConstraint.Z -> Vector3f(0f, 0f, movement.z)
                AxisConstraint.None -> movement
            }

            val axisConstrainedScaleAmount = Vector3f(1f).add(Vector3f(axisConstrainedMovement).mul(0.01f))
            val degreesX = Math.toDegrees(movement.y.toDouble()).toFloat() * 0.001f
            val degreesY = Math.toDegrees(movement.x.toDouble()).toFloat() * 0.001f

            fun handleTranslation() {
                when(editorComponents.transformSpace) {
                    TransformSpace.World -> entityOrNull.transform.set(Matrix4f(oldTransform).translateLocal(axisConstrainedMovement))
                    TransformSpace.Local -> entityOrNull.transform.set(Matrix4f(oldTransform).translate(axisConstrainedMovement))
                    TransformSpace.View -> entityOrNull.transform.set(Matrix4f(oldTransform).translate(activeCamera.getViewDirection().mul(axisConstrainedMovement)))
                }
            }
            fun handleRotation() {
                val cameraRight = activeCamera.getRightDirection()
                val cameraUp = activeCamera.getUpDirection()
                val cameraView = activeCamera.getViewDirection()

                val entityRight = entityOrNull.transform.rightDirection
                val entityUp = entityOrNull.transform.upDirection
                val entityView = entityOrNull.transform.viewDirection

                val pivot = editorComponents.sphereHolder.sphereEntity.transform.position

                when(editorComponents.rotateAround) {
                    RotateAround.Self -> when(editorComponents.transformSpace) {
                        TransformSpace.World -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, -1f, 0f, 0f))
                            AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, 0f, 1f, 0f))
                            AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, 0f, 0f, -1f))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.Local -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateX(degreesX))
                            AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateY(degreesY))
                            AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateZ(degreesY))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.View -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, cameraRight.x, cameraRight.y, cameraRight.z))
                            AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraUp.x, cameraUp.y, cameraUp.z))
                            AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraView.x, cameraView.y, cameraView.z))
                            AxisConstraint.None -> Unit
                        }
                    }
                    RotateAround.Pivot -> when(editorComponents.transformSpace) {
                        TransformSpace.World -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesX, -1f, 0f, 0f), pivot))
                            AxisConstraint.Y -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, 0f, 1f, 0f), pivot))
                            AxisConstraint.Z -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, 0f, 0f, -1f), pivot))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.Local -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesX, entityRight), pivot))
                            AxisConstraint.Y -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, entityUp), pivot))
                            AxisConstraint.Z -> entityOrNull.transform.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, entityView), pivot))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.View -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, cameraRight))
                            AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraUp))
                            AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraView))
                            AxisConstraint.None -> Unit
                        }
                    }
                }.let {  }


            }
            fun handleScaling() {

                when(editorComponents.transformSpace) {
                    TransformSpace.World -> when(constraintAxis) {
                            AxisConstraint.X -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount, entityOrNull.transform.position)
                            AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount, entityOrNull.transform.position)
                            AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount, entityOrNull.transform.position)
                            AxisConstraint.None -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount, entityOrNull.transform.position)
                        }.let {  }
                    TransformSpace.Local -> when(constraintAxis) {
                        AxisConstraint.X -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(entityOrNull.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(entityOrNull.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(entityOrNull.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.None -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount, entityOrNull.transform.position)
                    }.let {  }
                    TransformSpace.View -> when(constraintAxis) {
                        AxisConstraint.X -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(activeCamera.entity.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.Y -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(activeCamera.entity.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.Z -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(activeCamera.entity.transform.rotation), entityOrNull.transform.position)
                        AxisConstraint.None -> entityOrNull.transform.set(oldTransform).scaleAroundLocal(axisConstrainedScaleAmount.rotate(activeCamera.entity.transform.rotation), entityOrNull.transform.position)
                    }.let {  }
                }
            }
            when(editorComponents.transformMode) {
                TransformMode.Translate -> handleTranslation()
                TransformMode.Rotate -> handleRotation()
                TransformMode.Scale -> handleScaling()
                TransformMode.None -> Unit
            }.let {  }
        }
    }
}


fun Matrix4f.scaleAroundLocal(factor: Vector3f, o: Vector3f) = scaleAroundLocal(factor.x, factor.y, factor.z, o.x, o.y, o.z)
fun Matrix4f.scaleAroundLocal(factor: Vector3f, ox: Float, oy: Float, oz: Float) = scaleAroundLocal(factor.x, factor.y, factor.z, ox, oy, oz)
fun Matrix4f.rotateAroundLocal(axisAngle: AxisAngle4f, o: Vector3f): Matrix4f = rotateAroundLocal(Quaternionf(axisAngle), o.x, o.y, o.z)
fun Matrix4f.rotateAroundLocal(quat: Quaternionfc, o: Vector3f): Matrix4f = rotateAroundLocal(quat, o.x, o.y, o.z)
fun Matrix4f.rotateAffine(angle: Float, axis: Vector3f): Matrix4f = rotateAffine(angle, axis.x, axis.y, axis.z)
fun AxisAngle4f(angle: Float, axis: Vector3f) = AxisAngle4f(angle, axis.x, axis.y, axis.z)