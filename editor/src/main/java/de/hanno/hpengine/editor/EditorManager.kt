package de.hanno.hpengine.editor

import de.hanno.hpengine.editor.input.KeyUpDownProperty
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import org.joml.Vector3f
import java.awt.event.KeyEvent

class EditorManager(val addResourceContext: AddResourceContext, val editorComponents: EditorComponents) : Manager {
    private val editor = editorComponents.editor
    private var wPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_W, withShift = true)
    private var sPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_S, withShift = true)
    private var aPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_A, withShift = true)
    private var dPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_D, withShift = true)
    private var qPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_Q, withShift = true)
    private var ePressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_E, withShift = true)
    private var shiftPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_SHIFT, withShift = true)

    override suspend fun update(scene: Scene, deltaSeconds: Float) {

//        if (!editor.canvas?.containsMouse) return

        val turbo = if (this@EditorManager.shiftPressed) 3f else 1f

        val moveAmount = 100f * 0.1f * deltaSeconds * turbo

        val entity = scene.cameraEntity

        if (this@EditorManager.wPressed) {
            entity.transform.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (this@EditorManager.sPressed) {
            entity.transform.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (this@EditorManager.aPressed) {
            entity.transform.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (this@EditorManager.dPressed) {
            entity.transform.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (this@EditorManager.qPressed) {
            entity.transform.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (this@EditorManager.ePressed) {
            entity.transform.translate(Vector3f(0f, moveAmount, 0f))
        }
    }
}