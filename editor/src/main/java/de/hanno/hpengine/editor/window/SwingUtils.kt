package de.hanno.hpengine.editor.window

import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import java.awt.Component
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

object SwingUtils {
    fun invokeLater(block: () -> Unit) = if(SwingUtilities.isEventDispatchThread()) {
        block()
    } else {
        SwingUtilities.invokeLater(block)
    }

    fun <T> invokeAndWait(block: () -> T) = if(SwingUtilities.isEventDispatchThread()) {
        block()
    } else {
        var result: T? = null
        SwingUtilities.invokeAndWait {
            result = block()
        }
        result!!
    }
}

fun JPanel.setWithRefresh(createComponent: () -> Component) = SwingUtils.invokeLater {
    removeAll()
    add(ReloadableScrollPane(createComponent()), "wrap")
    revalidate()
    repaint()
}

fun JPanel.verticalBox(vararg comp: JComponent) = setWithRefresh {
    verticalBoxOf(*comp)
}

fun verticalBoxOf(vararg comp: JComponent): Box = Box.createVerticalBox().apply {
    comp.forEach { add(it) }
}