package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.transform.AABBData
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.io.File
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

    val emptySidePanel = JPanel().apply {
        preferredSize = Dimension(fixedWidth, canvas.height)

    }
    val sidePanel = object : JPanel() {
        override fun add(comp: Component): Component {
//            TODO: I am not able to set the size of the panel somehow
//            comp.preferredSize = Dimension(fixedWidth, comp.height)
            add(ReloadableScrollPane(comp), "wrap")
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
    val extension = EditorExtension(config, window.frame)
    val engineContext = EngineContext(config = config, additionalExtensions = listOf(extension), window = window, addResourceContext = addResourceContext)

    extension.engineContext = engineContext

    engineContext.init()

    val engine = Engine(engineContext)
    extension.editorComponents.init(engine)
    return Pair(engine, window)
}

fun main(args: Array<String>) {

    val config = ConfigImpl(
        Directories(
            gameDir = GameDirectory<RibbonEditor>(File("C:\\Users\\Tenter\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game")),
            engineDir = EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp"))
        ),
        debug = DebugConfig(isUseFileReloading = true)
    )

    val (engine) = EngineWithEditor(config)

    val loaded = LoadModelCommand(
        "assets/models/doom3monster/monster.md5mesh",
        "hellknight",
        engine.engineContext.extensions.materialExtension.manager,
        engine.config.directories.gameDir
    ).execute()
    loaded.entities.first().getComponent(ModelComponent::class.java)!!.spatial.boundingVolume.localAABB = AABBData(
            Vector3f(-60f, -10f, -35f),
            Vector3f(60f, 130f, 50f)
    )
    println("loaded entities : " + loaded.entities.size)
    engine.sceneManager.addAll(loaded.entities)
}
