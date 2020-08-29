package de.hanno.hpengine.editor.supportframes

import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.grids.ConfigGrid
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.eventBus
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.WindowConstants

class ConfigFrame(engine: EngineContext, config: ConfigImpl, editor: RibbonEditor): JFrame("Config") {
    init {
        val configPane = ReloadableScrollPane(ConfigGrid(config, engine.eventBus)).apply {
            this.preferredSize = Dimension(editor.canvas.width, editor.canvas.height)
        }
        size = Dimension(500, 500)
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        add(
                JPanel().apply {
                    layout = BorderLayout()
                    add(configPane, BorderLayout.CENTER)
                }
        )
        isVisible = true
    }
}