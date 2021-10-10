package de.hanno.hpengine.editor.manager

import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.graphics.editorTasks
import de.hanno.hpengine.editor.input.KeyUpDownProperty
import de.hanno.hpengine.editor.supportframes.ConfigFrame
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.extension.CameraExtension.Companion.cameraEntity
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Vector3f
import java.awt.event.KeyEvent

class EditorManager(
    val addResourceContext: AddResourceContext,
    val editor: RibbonEditor,
    val configFrame: ConfigFrame // TODO: I need this to be injected somewhere, find a better place
) : Manager {
    private var wPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_W, withShift = true)
    private var sPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_S, withShift = true)
    private var aPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_A, withShift = true)
    private var dPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_D, withShift = true)
    private var qPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_Q, withShift = true)
    private var ePressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_E, withShift = true)
    private var shiftPressed by KeyUpDownProperty(editor, addResourceContext, KeyEvent.VK_SHIFT, withShift = true)

    override suspend fun update(scene: Scene, deltaSeconds: Float) {

//        if (!editor.canvas?.containsMouse) return

        val turbo = if (shiftPressed) 3f else 1f

        val moveAmount = 100f * 0.1f * deltaSeconds * turbo

        val entity = scene.cameraEntity

        if (wPressed) {
            entity.transform.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (sPressed) {
            entity.transform.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (aPressed) {
            entity.transform.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (dPressed) {
            entity.transform.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (qPressed) {
            entity.transform.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (ePressed) {
            entity.transform.translate(Vector3f(0f, moveAmount, 0f))
        }
    }

    override fun onEntityAdded(entities: List<Entity>) {
        editor.ribbon.editorTasks.forEach { it.reloadContent() }
    }

    override fun onComponentAdded(component: Component) {
        editor.ribbon.editorTasks.forEach { it.reloadContent() }
    }

    override fun afterSetScene(lastScene: Scene?, currentScene: Scene) {
        editor.ribbon.editorTasks.forEach { it.reloadContent() }
    }

}