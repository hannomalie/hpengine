package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SingleThreadContext
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.awt.event.KeyEvent

class EditorManager(val editorComponents: EditorComponents) : Manager {
    private val editor = editorComponents.editor
    private var wPressed by KeyUpDownProperty(editor, KeyEvent.VK_W, withShift = true)
    private var sPressed by KeyUpDownProperty(editor, KeyEvent.VK_S, withShift = true)
    private var aPressed by KeyUpDownProperty(editor, KeyEvent.VK_A, withShift = true)
    private var dPressed by KeyUpDownProperty(editor, KeyEvent.VK_D, withShift = true)
    private var qPressed by KeyUpDownProperty(editor, KeyEvent.VK_Q, withShift = true)
    private var ePressed by KeyUpDownProperty(editor, KeyEvent.VK_E, withShift = true)
    private var shiftPressed by KeyUpDownProperty(editor, KeyEvent.VK_SHIFT, withShift = true)

    override fun SingleThreadContext.onEntityAdded(entities: List<Entity>) {
        editorComponents.sceneTree.reload()
    }

    override fun SingleThreadContext.onComponentAdded(component: Component) {
        editorComponents.sceneTree.reload()
    }
    override fun CoroutineScope.update(deltaSeconds: Float) {

//        if (!editor.canvas?.containsMouse) return

        val turbo = if (shiftPressed) 3f else 1f

        val moveAmount = 100f * 0.1f * deltaSeconds * turbo

        val entity = editorComponents.engine.scene.camera.entity

        if (wPressed) {
            entity.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (sPressed) {
            entity.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (aPressed) {
            entity.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (dPressed) {
            entity.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (qPressed) {
            entity.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (ePressed) {
            entity.translate(Vector3f(0f, moveAmount, 0f))
        }
    }

    override fun onSetScene(nextScene: Scene) {
        editorComponents.sceneTree.reload()
    }

}