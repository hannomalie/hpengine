package de.hanno.hpengine.editor.input

import de.hanno.hpengine.editor.RibbonEditor
import java.awt.Event.SHIFT_MASK
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

fun JComponent.addKeyActions(strokesAndActions: Map<KeyStroke, (ActionEvent) -> Unit>) {
    strokesAndActions.forEach { (stroke, action) ->
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, action)
        actionMap.put(action, object: AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                action(e)
            }
        })
    }
}
fun JComponent.addKeyActions(vararg strokesAndActions: Pair<KeyStroke, (ActionEvent) -> Unit>) {
    addKeyActions(strokesAndActions.toMap())
}

fun RibbonEditor.addKeyActions(strokesAndActions: Map<KeyStroke, (ActionEvent) -> Unit>) {
    rootPane.addKeyActions(strokesAndActions)
}

fun RibbonEditor.addKeyActions(vararg strokesAndActions: Pair<KeyStroke, (ActionEvent) -> Unit>) {
    rootPane.addKeyActions(strokesAndActions.toMap())
}

fun KMutableProperty0<Boolean>.addKeyUpDownForPropertyWithShift(editor: RibbonEditor, keyCode: Int) {
    addKeyUpDownForProperty(editor, keyCode)
    addKeyUpDownForProperty(editor, keyCode, SHIFT_MASK)
}
fun KMutableProperty0<Boolean>.addKeyUpDownForProperty(editor: RibbonEditor, keyCode: Int, modifiers: Int = 0) {
    editor.addKeyActions(
        KeyStroke.getKeyStroke(keyCode, modifiers) to { ae: ActionEvent -> set(true) },
        KeyStroke.getKeyStroke(keyCode, modifiers, true) to { ae: ActionEvent -> set(false) }
    )
}

class KeyUpDownProperty(editor: RibbonEditor, keyCode: Int, modifiers: Int = 0, withShift: Boolean = false) {
    private var value = false

    init {
        if(withShift) {
            ::value.addKeyUpDownForPropertyWithShift(editor, keyCode)
        } else {
            ::value.addKeyUpDownForProperty(editor, keyCode, modifiers)
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        this.value = value
    }
}