package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
import java.awt.event.KeyEvent

class EditorManager(val editor: RibbonEditor) : Manager {
    override fun CoroutineScope.update(deltaSeconds: Float) {

        if(!editor.mainPanel.containsMouse) return

        val turbo = if (editor.isKeyPressed(KeyEvent.VK_SHIFT)) 3f else 1f

        val moveAmount = 100f * 0.1f * deltaSeconds * turbo

        val entity = editor.engine.scene.camera.entity

        if (editor.isKeyPressed(KeyEvent.VK_W)) {
            entity.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (editor.isKeyPressed(KeyEvent.VK_S)) {
            entity.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (editor.isKeyPressed(KeyEvent.VK_A)) {
            entity.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (editor.isKeyPressed(KeyEvent.VK_D)) {
            entity.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (editor.isKeyPressed(KeyEvent.VK_Q)) {
            entity.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (editor.isKeyPressed(KeyEvent.VK_E)) {
            entity.translate(Vector3f(0f, moveAmount, 0f))
        }
    }
}