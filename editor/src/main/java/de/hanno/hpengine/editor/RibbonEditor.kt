package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.backend.EngineContextImpl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.executeInitScript
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.retrieveConfig
import net.miginfocom.swing.MigLayout
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JPanel

class RibbonEditor : JRibbonFrame("HPEngine") {
    init {
        isFocusable = true
        focusTraversalKeysEnabled = false
        preferredSize = Dimension(1280, 720)
    }

    lateinit var canvas: CustomGlCanvas
        private set

    fun init(canvas: CustomGlCanvas) {
        this.canvas = canvas
        add(canvas, BorderLayout.CENTER)
    }

    val sidePanel = JPanel().apply {
        layout = MigLayout("wrap 1")
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)
        this@RibbonEditor.add(this, BorderLayout.LINE_END)
    }

    fun setEngine(engine: EngineImpl, config: ConfigImpl) {
        EditorComponents(engine, config, this)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val config = retrieveConfig(args)

            val window = AWTEditor(config)
            val engineContext = EngineContextImpl(config = config, window = window)
            val renderer: RenderSystem = ExtensibleDeferredRenderer(engineContext)
            val engine = EngineImpl(
                    engineContext = engineContext,
                    renderer = renderer
            )
            window.init(engine, config)

            engine.executeInitScript()

        }
    }
}


fun JPanel.doWithRefresh(addContent: JPanel.() -> Unit) {
    SwingUtils.invokeLater {
        removeAll()
        addContent()
        revalidate()
        repaint()
    }
}
