package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.transform.AABBData
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

class RibbonEditor(config: ConfigImpl, val canvas: CustomGlCanvas) : JRibbonFrame("HPEngine") {
    var onSceneReload: (() -> Unit)? = null

    init {
        isFocusable = true
        focusTraversalKeysEnabled = false
        preferredSize = Dimension(config.width, config.height)
        defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        add(canvas, BorderLayout.CENTER)
        SwingUtils.invokeAndWait {
            pack()
        }
        isVisible = true
        transferFocus()
    }

    val emptySidePanel = JPanel()
    val sidePanel = object : JPanel() {
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

fun EngineWithEditor(config: ConfigImpl = ConfigImpl()): Pair<Engine, AWTEditorWindow> {
    val addResourceContext = AddResourceContext()
    val window = AWTEditorWindow(config, addResourceContext)
    val engineContext = EngineContext(config = config, window = window, addResourceContext = addResourceContext)
    val extension = EditorExtension(engineContext, config, window.frame)
    engineContext.add(extension)

    val engine = Engine(engineContext)
    extension.editorComponents.init(engine)
    return Pair(engine, window)
}

fun main(args: Array<String>) {
    val (engine) = EngineWithEditor()

    val loaded = LoadModelCommand("assets/models/doom3monster/monster.md5mesh", "hellknight", engine.scene.materialManager, engine.config.directories.gameDir).execute()
    loaded.entities.first().getComponent(ModelComponent::class.java)!!.spatial.boundingVolume.localAABB = AABBData(
            Vector3f(-60f, -10f, -35f),
            Vector3f(60f, 130f, 50f)
    )
    println("loaded entities : " + loaded.entities.size)
    engine.sceneManager.addAll(loaded.entities)

}
