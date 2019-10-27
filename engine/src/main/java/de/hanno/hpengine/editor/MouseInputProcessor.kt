package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.entity.Entity
import org.joml.Matrix4f
import org.joml.Vector3f
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.reflect.KProperty0

class MouseInputProcessor(val engine: Engine<*>, val selectedEntity: KProperty0<Entity?>, val editor: RibbonEditor) : MouseAdapter() {
    private var lastX: Float? = null
    private var lastY: Float? = null
    private val oldTransform = Matrix4f()

    private var pitch = 0f
    private var yaw = 0f

    override fun mouseMoved(e: MouseEvent) { }
    override fun mousePressed(e: MouseEvent) {
        lastX = e.x.toFloat()
        lastY = e.y.toFloat()

        val entityOrNull = selectedEntity.call()
        entityOrNull?.transformation?.get(oldTransform)
    }

    override fun mouseReleased(e: MouseEvent?) {
        val entityOrNull = selectedEntity.call()
        entityOrNull?.transformation?.get(oldTransform)
    }

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 10.1f * 0.05 * rotationDelta

        val deltaX = (lastX ?: e.x.toFloat()) - e.x.toFloat()
        val deltaY = (lastY ?: e.y.toFloat()) - e.y.toFloat()
        println("DeltaX $deltaX, DeltaY $deltaY")

        val pitchAmount = Math.toRadians((deltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((-deltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val entityOrNull = selectedEntity.call()
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
            when(editor.constraintAxis) {
                AxisConstraint.X -> entityOrNull.set(Matrix4f().translation(Vector3f(moveAmountX, 0f, 0f)).mul(oldTransform))
                AxisConstraint.Y -> entityOrNull.set(Matrix4f().translation(Vector3f(0f, moveAmountY, 0f)).mul(oldTransform))
                AxisConstraint.Z -> entityOrNull.set(Matrix4f().translation(Vector3f(0f, 0f, moveAmountY)).mul(oldTransform))
            }
        }
    }
}