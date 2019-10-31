package de.hanno.hpengine.editor

import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel

class MainPanel : JPanel() {
    var containsMouse = true

    init {
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                containsMouse = true
            }

            override fun mouseExited(e: MouseEvent?) {
                containsMouse = false
            }
        })
    }

    fun setContent(content: Component) = doWithRefresh {
        add(content)
    }

}