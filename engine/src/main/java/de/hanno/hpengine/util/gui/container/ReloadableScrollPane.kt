package de.hanno.hpengine.util.gui.container

import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants

class ReloadableScrollPane(val child: Component, fixedWidth: Int? = null) : JScrollPane(child) {
    init {
        fixedWidth?.let { preferredSize.width = it }
        setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
        setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED)
    }

    fun reload() {
        setViewportView(child)
        child.revalidate()
        child.repaint()

        rootPane.invalidate()
        rootPane.revalidate()
        rootPane.repaint()
    }
}