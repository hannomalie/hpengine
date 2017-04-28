package de.hanno.hpengine.editor

import com.alee.extended.panel.WebAccordion
import com.alee.extended.panel.WebAccordionStyle
import com.alee.laf.WebLookAndFeel
import com.alee.laf.checkbox.WebCheckBox
import com.alee.laf.menu.WebMenu
import com.alee.laf.menu.WebMenuBar
import com.alee.laf.menu.WebMenuItem
import com.alee.laf.panel.WebPanel
import com.alee.laf.rootpane.WebFrame
import com.alee.laf.scroll.WebScrollPane
import com.alee.laf.splitpane.WebSplitPane
import com.alee.laf.tabbedpane.WebTabbedPane
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.engine.CanvasWrapper
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.event.GlobalDefineChangedEvent
import de.hanno.hpengine.event.bus.EventBus
import de.hanno.hpengine.renderer.GraphicsContext
import de.hanno.hpengine.renderer.Renderer
import de.hanno.hpengine.scene.Scene
import de.hanno.hpengine.util.Toggable
import de.hanno.hpengine.util.gui.AddEntityView
import de.hanno.hpengine.util.gui.HostComponent
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.util.*
import java.util.logging.Logger
import javax.swing.JPanel

class Editor : WebFrame(), HostComponent {

    private val logger = Logger.getLogger(Editor::class.java.name)

    private val setTitleRunnable = Runnable {
        try {
            title = String.format("Render %03.0f fps | %03.0f ms - Update %03.0f fps | %03.0f ms",
                    Renderer.getInstance().currentFPS, Renderer.getInstance().msPerFrame, Engine.getInstance().fpsCounter.fps, Engine.getInstance().fpsCounter.msPerFrame)
        } catch (e: Exception) {
            title = "HPEngine Renderer initializing..."
        }
    }
    val canvasWrapper = CanvasWrapper(Canvas(), setTitleRunnable)
    private val topPanel = WebTabbedPane()
    private val leftPanel = WebPanel()
    private val editorPanel = object : JPanel() {
        init {
            preferredSize.height = 600
            EventBus.getInstance().register(this)
            layout = BorderLayout(0, 0)
            removeAll()
            add(canvasWrapper.canvas, BorderLayout.CENTER)
            addComponentListener(object : ComponentListener {
                override fun componentResized(e: ComponentEvent) {
                    onResize(canvasWrapper)
                }

                override fun componentMoved(e: ComponentEvent) {
                }

                override fun componentShown(e: ComponentEvent) {
                }

                override fun componentHidden(e: ComponentEvent) {
                }
            })
        }
    }

    private fun onResize(canvasWrapper1: CanvasWrapper) {
        if (GraphicsContext.getInstance().isAttachedTo(canvasWrapper1)) {
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(evt: ComponentEvent?) {
                    try {
                        GraphicsContext.getInstance().canvasWidth = canvasWrapper1.canvas.width
                        GraphicsContext.getInstance().canvasHeight = canvasWrapper1.canvas.height
                    } catch (e: IllegalStateException) {
                        //TODO: Do logging
                    }
                }
            })
        }
    }

    init {
        WebLookAndFeel.install()
        addMenu()
        addTopPanelTabs()
        addLeftPanelTabs()

        var verticalSplitPane = WebSplitPane(WebSplitPane.VERTICAL_SPLIT)
        with(verticalSplitPane) {
            resizeWeight = 0.75
            isResizable = true
            add(editorPanel)
            add(topPanel)
        }
        var horizontalSplitPane = WebSplitPane(WebSplitPane.HORIZONTAL_SPLIT)
        with(horizontalSplitPane) {
            resizeWeight = 0.2
            isResizable = true
            add(leftPanel)
            add(verticalSplitPane)
        }
        add(horizontalSplitPane, BorderLayout.CENTER)
        isVisible = true
        size = Dimension(Config.getInstance().width, Config.getInstance().height)
        defaultCloseOperation = EXIT_ON_CLOSE
        //TODO EXIT ON CLOSE
    }

    private fun addMenu() {
        val menuBar = WebMenuBar()
        menuBar.isUndecorated = true

        val menuScene = WebMenu("Scene")
        val sceneSaveMenuItem = WebMenuItem("New")
        sceneSaveMenuItem.addActionListener({ Engine.getInstance().scene = Scene() })
        menuScene.add(sceneSaveMenuItem)

        val menuEntity = WebMenu("Entity")
        val addEntityMenuItem = WebMenuItem("Add")
        addEntityMenuItem.addActionListener({
//            AddEntityView(Engine.getInstance(), this, null).show()

            var addEntityFrame = WebFrame("Add Entity")
            addEntityFrame.setSize(600, 300)
            addEntityFrame.add(AddEntityView(Engine.getInstance(), addEntityFrame, this))
            addEntityFrame.setVisible(true)
        })
        menuEntity.add(addEntityMenuItem)

        menuBar.add(menuScene)
        menuBar.add(menuEntity)
        jMenuBar = menuBar
    }

    private fun addLeftPanelTabs() {
        val accordion = WebAccordion(WebAccordionStyle.accordionStyle)
        accordion.setMultiplySelectionAllowed(false)
        accordion.addPane(null, "Entity", EntityView())

        leftPanel.add(accordion)
    }

    private fun addTopPanelTabs() {
        val tabs = HashMap<String, JPanel>()
        for (field in Config.getInstance().javaClass.declaredFields) {
            if (!field.isAccessible) {
                field.isAccessible = true
            }
            for (annotation in field.declaredAnnotations) {
                when (annotation) {
                    is Toggable -> {
                        val panel: JPanel? = if (!tabs.containsKey(annotation.group)) {
                            val newPanel = JPanel()
                            newPanel.layout = FlowLayout()
                            newPanel.preferredSize = Dimension(-1, 100)
                            tabs.put(annotation.group, newPanel)
                        } else {
                            tabs.get(annotation.group)
                        }

                        val button = WebCheckBox(field.name, field.getBoolean(Config.getInstance()))
                        button.addActionListener { e ->
                            val currentValue: Boolean
                            try {
                                currentValue = field.getBoolean(Config.getInstance())
                                field.setBoolean(Config.getInstance(), !currentValue)
                                Engine.getEventBus().post(GlobalDefineChangedEvent())
                            } catch (e1: Exception) {
                                e1.printStackTrace()
                            }
                        }
                        panel?.add(button)
                    }
                }
            }
        }
        for ((name, tab) in tabs) {
            topPanel.addTab(name, WebScrollPane(tab))
        }
    }

    override fun startProgress(startProgress: String?) {
        logger.info { startProgress }
    }

    override fun showError(error: String?) {
        logger.severe { error }
    }

    override fun stopProgress() {
        logger.info { "Progress finished... " }
    }
}

fun main(args: Array<String>) {
    val editor = Editor()
    Engine.init(editor.canvasWrapper)
}