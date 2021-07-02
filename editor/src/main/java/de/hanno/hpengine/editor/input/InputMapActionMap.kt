package de.hanno.hpengine.editor.input

import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.scene.AddResourceContext
import java.awt.Event.SHIFT_MASK
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

fun JComponent.addKeyActions(addResourceContext: AddResourceContext, strokesAndActions: Map<KeyStroke, (ActionEvent) -> Unit>) {
    strokesAndActions.forEach { (stroke, action) ->
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, action)
        actionMap.put(action, object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent) = addResourceContext.launch {
                action(e)
            }
        })
    }
}
fun RibbonEditor.addKeyActions(addResourceContext: AddResourceContext, vararg strokesAndActions: Pair<KeyStroke, (ActionEvent) -> Unit>) {
    rootPane.addKeyActions(addResourceContext, strokesAndActions.toMap())
}

fun KMutableProperty0<Boolean>.addKeyUpDownForPropertyWithShift(editor: RibbonEditor, addResourceContext: AddResourceContext, keyCode: Int) {
    addKeyUpDownForProperty(editor, addResourceContext, keyCode)
    addKeyUpDownForProperty(editor, addResourceContext, keyCode, SHIFT_MASK)
}
fun KMutableProperty0<Boolean>.addKeyUpDownForProperty(editor: RibbonEditor, addResourceContext: AddResourceContext, keyCode: Int, modifiers: Int = 0) {
    editor.addKeyActions(
        addResourceContext,
        KeyStroke.getKeyStroke(keyCode, modifiers) to { ae: ActionEvent -> set(true) },
        KeyStroke.getKeyStroke(keyCode, modifiers, true) to { ae: ActionEvent -> set(false) }
    )
}

class KeyUpDownProperty(editor: RibbonEditor, addResourceContext: AddResourceContext, keyCode: Int, modifiers: Int = 0, withShift: Boolean = false) {
    private var value = false

    init {
        if(withShift) {
            ::value.addKeyUpDownForPropertyWithShift(editor, addResourceContext, keyCode)
        } else {
            ::value.addKeyUpDownForProperty(editor, addResourceContext, keyCode, modifiers)
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        this.value = value
    }
}