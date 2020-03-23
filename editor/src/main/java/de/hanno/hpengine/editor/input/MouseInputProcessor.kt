package de.hanno.hpengine.editor.input

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.selection.EntitySelection
import de.hanno.hpengine.editor.selection.Selection
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.reflect.KProperty0

class MouseInputProcessor(val engine: Engine<*>,
                          val selection: KProperty0<Selection>,
                          val editorComponents: EditorComponents) : MouseAdapter() {
    private var lastX: Float? = null
    private var lastY: Float? = null
    private val oldTransform = Matrix4f()

    private var pitch = 0f
    private var yaw = 0f

    override fun mouseMoved(e: MouseEvent) { }
    override fun mousePressed(e: MouseEvent) {
        lastX = e.x.toFloat()
        lastY = e.y.toFloat()

        val entityOrNull = getEntityOrNull()
        entityOrNull?.transformation?.get(oldTransform)
    }

    private fun getEntityOrNull() = (selection.call() as? EntitySelection)?.entity

    override fun mouseReleased(e: MouseEvent?) {
        val entityOrNull = getEntityOrNull()
        entityOrNull?.transformation?.get(oldTransform)
    }

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 2.0f * 0.05 * rotationDelta

        val deltaX = (lastX ?: e.x.toFloat()) - e.x.toFloat()
        val deltaY = (lastY ?: e.y.toFloat()) - e.y.toFloat()

        val pitchAmount = Math.toRadians((deltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((-deltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val entityOrNull = getEntityOrNull()
        if(entityOrNull == null) {
            val entity = engine.scene.camera.entity
            val oldTranslation = entity.getTranslation(Vector3f())
            entity.setTranslation(Vector3f(0f, 0f, 0f))
            entity.rotationX(pitchAmount.toFloat())
            entity.rotateLocalY((-yawAmount).toFloat())
            entity.translateLocal(oldTranslation)
        } else {
            val turbo = if (editorComponents.isKeyPressed(KeyEvent.VK_SHIFT)) 3f else 1f

            val moveAmountX = 0.1f * deltaX * turbo
            val moveAmountY = 0.1f * deltaY * turbo
            val degreesX = Math.toDegrees(moveAmountX.toDouble()).toFloat() * 0.0001f
            val degreesY = Math.toDegrees(moveAmountX.toDouble()).toFloat() * 0.0001f

            fun handleTranslation() {
                when(editorComponents.transformSpace) {
                    TransformSpace.World -> when(editorComponents.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(moveAmountX, 0f, 0f)))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(0f, moveAmountY, 0f)))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(0f, 0f, moveAmountY)))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.Local -> when(editorComponents.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(moveAmountX, 0f, 0f)))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(0f, moveAmountY, 0f)))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(0f, 0f, moveAmountY)))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.View -> when(editorComponents.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).translate(engine.scene.activeCamera.getRightDirection().mul(Vector3f(moveAmountX, 0f, 0f))))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).translate(engine.scene.activeCamera.getUpDirection().mul(Vector3f(0f, moveAmountY, 0f))))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).translate(engine.scene.activeCamera.getViewDirection().mul(Vector3f(0f, 0f, moveAmountY))))
                        AxisConstraint.None -> {
                            val x = Vector3f(engine.scene.activeCamera.getRightDirection()).mul(-moveAmountX)
                            val y = Vector3f(engine.scene.activeCamera.getUpDirection()).mul(moveAmountY)
                            x.add(y)
                            entityOrNull.set(Matrix4f(oldTransform).translate(x))
                        }
                    }
                }
            }
            fun handleRotation() {
                val cameraRight = engine.scene.activeCamera.getRightDirection()
                val cameraUp = engine.scene.activeCamera.getUpDirection()
                val cameraView = engine.scene.activeCamera.getViewDirection()

                val entityRight = entityOrNull.rightDirection
                val entityUp = entityOrNull.upDirection
                val entityView = entityOrNull.viewDirection

                val pivot = editorComponents.sphereHolder.sphereEntity.position

                when(editorComponents.rotateAround) {
                    RotateAround.Self -> when(editorComponents.transformSpace) {
                        TransformSpace.Local -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateX(degreesX))
                            AxisConstraint.Y -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateY(degreesY))
                            AxisConstraint.Z -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateZ(degreesY))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.World -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, 1f, 0f, 0f))
                            AxisConstraint.Y -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, 0f, 1f, 0f))
                            AxisConstraint.Z -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, 0f, 0f, 1f))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.View -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, cameraRight.x, cameraRight.y, cameraRight.z))
                            AxisConstraint.Y -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraUp.x, cameraUp.y, cameraUp.z))
                            AxisConstraint.Z -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraView.x, cameraView.y, cameraView.z))
                            AxisConstraint.None -> Unit
                        }
                    }
                    RotateAround.Pivot -> when(editorComponents.transformSpace) {
                        TransformSpace.Local -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesX, entityRight), pivot))
                            AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, entityUp), pivot))
                            AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, entityView), pivot))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.World -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesX, 1f, 0f, 0f), pivot))
                            AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, 0f, 1f, 0f), pivot))
                            AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).rotateAroundLocal(AxisAngle4f(degreesY, 0f, 0f, 1f), pivot))
                            AxisConstraint.None -> Unit
                        }
                        TransformSpace.View -> when(editorComponents.constraintAxis) {
                            AxisConstraint.X -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesX, cameraRight))
                            AxisConstraint.Y -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraUp))
                            AxisConstraint.Z -> entityOrNull.set(oldTransform).mul(Matrix4f().identity().rotateAffine(degreesY, cameraView))
                            AxisConstraint.None -> Unit
                        }
                    }
                }.let {  }


            }
            fun handleScaling() {
                when(editorComponents.constraintAxis) {
//                    AxisConstraint.X -> entityOrNull.set(Matrix4f().scale(Vector3f(1f + moveAmountX, 1f, 1f))).mul(oldTransform)
//                    AxisConstraint.Y -> entityOrNull.set(Matrix4f().scale(Vector3f(1f, 1f + moveAmountY, 1f))).mul(oldTransform)
//                    AxisConstraint.Z -> entityOrNull.set(Matrix4f().scale(Vector3f(1f, 1f, 1f + moveAmountY))).mul(oldTransform)
//                    AxisConstraint.None -> entityOrNull.set(Matrix4f().scale(Vector3f(1f + moveAmountY))).mul(oldTransform)
                    AxisConstraint.X -> entityOrNull.scaleAroundLocal(1f + moveAmountX, 1f, 1f, entityOrNull.position.x, entityOrNull.position.y, entityOrNull.position.z)
                    AxisConstraint.Y -> entityOrNull.scaleAroundLocal(1f, 1f + moveAmountY, 1f, entityOrNull.position.x, entityOrNull.position.y, entityOrNull.position.z)
                    AxisConstraint.Z -> entityOrNull.scaleAroundLocal(1f, 1f, 1f + moveAmountY, entityOrNull.position.x, entityOrNull.position.y, entityOrNull.position.z)
                    AxisConstraint.None -> entityOrNull.scaleAroundLocal(1f + moveAmountX, 1f + moveAmountY, 1f, entityOrNull.position.x, entityOrNull.position.y, entityOrNull.position.z)
                }.let {  }
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

fun Matrix4f.rotateAroundLocal(axisAngle: AxisAngle4f, o: Vector3f): Matrix4f = rotateAroundLocal(Quaternionf(axisAngle), o.x, o.y, o.z)
fun Matrix4f.rotateAroundLocal(quat: Quaternionfc, o: Vector3f): Matrix4f = rotateAroundLocal(quat, o.x, o.y, o.z)
fun Matrix4f.rotateAffine(angle: Float, axis: Vector3f): Matrix4f = rotateAffine(angle, axis.x, axis.y, axis.z)
fun AxisAngle4f(angle: Float, axis: Vector3f) = AxisAngle4f(angle, axis.x, axis.y, axis.z)