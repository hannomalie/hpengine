package de.hanno.hpengine.editor

import de.hanno.hpengine.editor.modules.editorModule
import de.hanno.hpengine.editor.modules.editorWindowModule
import de.hanno.hpengine.editor.window.AWTEditorWindow
import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.config.DebugConfig
import de.hanno.hpengine.engine.directory.Directories
import de.hanno.hpengine.engine.directory.EngineDirectory
import de.hanno.hpengine.engine.directory.GameDirectory
import de.hanno.hpengine.engine.extension.baseModule
import de.hanno.hpengine.engine.extension.deferredRendererModule
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.OpenGlExecutorImpl
import de.hanno.hpengine.engine.scene.dsl.Directory
import de.hanno.hpengine.engine.scene.dsl.StaticModelComponentDescription
import de.hanno.hpengine.engine.scene.dsl.convert
import de.hanno.hpengine.engine.scene.dsl.entity
import de.hanno.hpengine.engine.scene.dsl.scene
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import net.miginfocom.swing.MigLayout
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JPanel

class RibbonEditor(
    config: ConfigImpl,
    executor: OpenGlExecutorImpl
) : JRibbonFrame("HPEngine") {
    var onSceneReload: (() -> Unit)? = null

    val canvas: CustomGlCanvas = CustomGlCanvas(config, executor)
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
        canvas.init()
    }

    val emptySidePanel = JPanel().apply {
        preferredSize = Dimension(fixedWidth, canvas.height)
        size = Dimension(fixedWidth, canvas.height)
        add(ReloadableScrollPane(JPanel()), "wrap")
    }
    val sidePanel = object : JPanel() {
        override fun add(comp: Component): Component {
//            TODO: I am not able to set the size of the panel somehow
            comp.preferredSize = Dimension(fixedWidth, comp.height)
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

fun EngineWithEditor(config: ConfigImpl = ConfigImpl()) = Engine(
    startKoin {
        modules(config.toModule(), editorWindowModule, editorModule, baseModule, deferredRendererModule)
    }
)

private fun ConfigImpl.toModule(): Module = module {
    single { this@toModule } bind Config::class
}

fun main() {

    val config = ConfigImpl(
        Directories(
            gameDir = GameDirectory<RibbonEditor>(File("C:\\Users\\Tenter\\workspace\\hpengine\\newsimplegame\\src\\main\\resources\\game")),
            engineDir = EngineDirectory(File("C:\\Users\\Tenter\\workspace\\hpengine\\engine\\src\\main\\resources\\hp"))
        ),
        debug = DebugConfig(isUseFileReloading = true)
    )

    val engine = EngineWithEditor(config)

    engine.application.koin.get<AWTEditorWindow>().apply {
        frame.onSceneReload = {
            engine.scene = scene("Hellknight") {
                entity("hellknight") {
//                    add(
//                        AnimatedModelComponentDescription(
//                            "assets/models/doom3monster/monster.md5mesh",
//                            Directory.Game,
//                            AABBData(
//                                Vector3f(-60f, -10f, -35f),
//                                Vector3f(60f, 130f, 50f)
//                            )
//                        )
//                    )
                    add(
                        StaticModelComponentDescription(
                            "assets/models/sponza.obj",
                            Directory.Game,
                        )
                    )
                }
            }.convert(engine.application.koin.get(), engine.application.koin.get())
        }.apply { invoke() }
    }
}
