package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.reflect.KProperty0

class MouseInputProcessor(val engine: Engine<*>, val selectedEntity: KProperty0<Any?>, val editor: RibbonEditor) : MouseAdapter() {
    private var lastX: Float? = null
    private var lastY: Float? = null
    private val oldTransform = Matrix4f()

    private var pitch = 0f
    private var yaw = 0f

    override fun mouseMoved(e: MouseEvent) { }
    override fun mousePressed(e: MouseEvent) {
        lastX = e.x.toFloat()
        lastY = e.y.toFloat()

        val entityOrNull = selectedEntity.call() as? Entity
        entityOrNull?.transformation?.get(oldTransform)
    }

    override fun mouseReleased(e: MouseEvent?) {
        val entityOrNull = selectedEntity.call() as? Entity
        entityOrNull?.transformation?.get(oldTransform)
    }

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 10.1f * 0.05 * rotationDelta

        val deltaX = (lastX ?: e.x.toFloat()) - e.x.toFloat()
        val deltaY = (lastY ?: e.y.toFloat()) - e.y.toFloat()

        val pitchAmount = Math.toRadians((deltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((-deltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val entityOrNull = selectedEntity.call() as? Entity
        if(entityOrNull == null) {
            val entity = engine.scene.camera.entity
            val oldTranslation = entity.getTranslation(Vector3f())
            entity.setTranslation(Vector3f(0f, 0f, 0f))
            entity.rotationX(pitchAmount.toFloat())
            entity.rotateLocalY((-yawAmount).toFloat())
            entity.translateLocal(oldTranslation)
        } else {
            val turbo = if (editor.isKeyPressed(KeyEvent.VK_SHIFT)) 3f else 1f

            val moveAmountX = deltaX * turbo
            val moveAmountY = deltaY * turbo
            val degreesX = Math.toDegrees(moveAmountX.toDouble()).toFloat() * 0.0001f
            val degreesY = Math.toDegrees(moveAmountX.toDouble()).toFloat() * 0.0001f

            fun handleTranslation() {
                when(editor.transformSpace) {
                    TransformSpace.World -> when(editor.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(moveAmountX, 0f, 0f)))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(0f, moveAmountY, 0f)))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).translateLocal(Vector3f(0f, 0f, moveAmountY)))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.Local -> when(editor.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(moveAmountX, 0f, 0f)))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(0f, moveAmountY, 0f)))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).translate(Vector3f(0f, 0f, moveAmountY)))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.View -> {
                        TODO()
                    }
                }
            }
            fun handleRotation() {
                when(editor.transformSpace) {
                    TransformSpace.World -> when(editor.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).rotateLocalX(degreesX))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).rotateLocalY(degreesY))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).rotateLocalZ(degreesY))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.Local -> when(editor.constraintAxis) {
                        AxisConstraint.X -> entityOrNull.set(Matrix4f(oldTransform).rotateX(degreesX))
                        AxisConstraint.Y -> entityOrNull.set(Matrix4f(oldTransform).rotateY(degreesY))
                        AxisConstraint.Z -> entityOrNull.set(Matrix4f(oldTransform).rotateZ(degreesY))
                        AxisConstraint.None -> Unit
                    }
                    TransformSpace.View -> {
                        TODO()
                    }
                }

            }
            fun handleScaling() {
                when(editor.constraintAxis) {
                    AxisConstraint.X -> entityOrNull.set(Matrix4f().scale(1f + moveAmountX)).mul(oldTransform)
                    AxisConstraint.Y -> entityOrNull.set(Matrix4f().scale(1f + moveAmountY)).mul(oldTransform)
                    AxisConstraint.Z -> entityOrNull.set(Matrix4f().scale(1f + moveAmountY)).mul(oldTransform)
                }
            }
            when(editor.transformMode) {
                TransformMode.Translate -> handleTranslation()
                TransformMode.Rotate -> handleRotation()
                TransformMode.Scale -> handleScaling()
            }
        }
    }
}