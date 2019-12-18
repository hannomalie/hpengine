package de.hanno.hpengine.editor

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