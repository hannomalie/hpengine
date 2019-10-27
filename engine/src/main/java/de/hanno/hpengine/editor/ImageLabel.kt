package de.hanno.hpengine.editor

import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JLabel

class ImageLabel(image: Icon, val editor: RibbonEditor) : JLabel(image) {
    val mainPanel = editor.mainPanel

    override fun getPreferredSize(): Dimension {
        return Dimension(mainPanel.width, mainPanel.height)
    }

    override fun paint(g: Graphics) {
        g.drawImage(editor.image, 0, 0, this.width, this.height, null)
    }
}