package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
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
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
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

    val emptySidePanel = JPanel()
    val sidePanel = object: JPanel() {
        override fun add(comp: Component): Component {
            comp.preferredSize = Dimension(fixedWidth, 800)
            add(comp, "wrap")
            return this
        }
    }.apply {
        add(emptySidePanel)
        layout = MigLayout("wrap 1")
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)
        this@RibbonEditor.add(this, BorderLayout.LINE_END)
    }

    fun setEngine(engine: Engine, config: ConfigImpl) {
        EditorComponents(engine, config, this)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val config = retrieveConfig(args)

            val window = AWTEditor(config)
            val engineContext = EngineContext(config = config, window = window)
            val renderer: RenderSystem = ExtensibleDeferredRenderer(engineContext)
            val engine = Engine(
                    engineContext = engineContext,
                    renderer = renderer
            )
            window.init(engine, config)

            engine.executeInitScript()

        }
    }
}

val fixedWidth = 300

fun JPanel.doWithRefresh(addContent: JPanel.() -> Unit) {
    SwingUtils.invokeLater {
        removeAll()
        addContent()
        revalidate()
        repaint()
    }
}

fun JPanel.verticalBox(vararg comp: JComponent) = doWithRefresh {
    removeAll()
    add(verticalBoxOf(*comp))
}

fun verticalBoxOf(vararg comp: JComponent): Box {
    return Box.createVerticalBox().apply {
        comp.forEach { add(it) }
    }
}
