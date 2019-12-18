package de.hanno.hpengine.editor

import java.awt.event.KeyEvent
import java.awt.event.KeyListener

class KeyLogger : KeyListener {
    val pressedKeys = mutableSetOf<Int>()
    override fun keyTyped(e: KeyEvent) { }

    override fun keyPressed(e: KeyEvent) {
        pressedKeys.add(e.keyCode)
    }

    override fun keyReleased(e: KeyEvent) {
        pressedKeys.remove(e.keyCode)
    }
}