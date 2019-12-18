package de.hanno.hpengine.editor

import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JLabel

class ImageLabel(image: Icon, val editorComponents: EditorComponents) : JLabel(image) {

    override fun getPreferredSize(): Dimension {
        return Dimension(editorComponents.editor.centerComponent.width, editorComponents.editor.centerComponent.height)
    }

    override fun paint(g: Graphics) {
        g.drawImage(editorComponents.image, 0, 0, this.width, this.height, null)
    }
}