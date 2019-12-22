package de.hanno.hpengine.editor

import de.hanno.hpengine.editor.appmenu.ApplicationMenu
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.EditorInputConfigImpl
import de.hanno.hpengine.editor.input.KeyLogger
import de.hanno.hpengine.editor.input.MouseInputProcessor
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.supportframes.ConfigFrame
import de.hanno.hpengine.editor.supportframes.TimingsFrame
import de.hanno.hpengine.editor.tasks.MaterialTask
import de.hanno.hpengine.editor.tasks.SceneTask
import de.hanno.hpengine.editor.tasks.TextureTask
import de.hanno.hpengine.editor.tasks.TransformTask
import de.hanno.hpengine.editor.tasks.ViewTask
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.neon.api.icon.ResizableIcon
import org.pushingpixels.photon.api.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import javax.swing.BorderFactory


class EditorComponents(val engine: EngineImpl,
                       val config: ConfigImpl,
                       val editor: RibbonEditor) : RenderSystem, EditorInputConfig by EditorInputConfigImpl() {

    private val ribbon = editor.ribbon
    private val sidePanel = editor.sidePanel
    val sphereHolder = SphereHolder(engine)

    val sceneTree = SwingUtils.invokeAndWait {
        SceneTree(engine, this).apply {
            addDefaultMouseListener()
            SwingUtils.invokeLater {
                ReloadableScrollPane(this).apply {
                    preferredSize = Dimension(300, editor.sidePanel.height)
                    border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color.BLACK)
                    editor.add(this, BorderLayout.LINE_START)
                }
            }
        }
    }

    val selectionSystem = SelectionSystem(this)
    val textureRenderer = SimpleTextureRenderer(engine, engine.deferredRenderingBuffer.colorReflectivenessTexture)

    override fun render(result: DrawResult, state: RenderState) {
        selectionSystem.render(result, state)
        sphereHolder.render(result, state)
        if(config.debug.isUseDirectTextureOutput) {
            textureRenderer.drawToQuad(engine.window.frontBuffer, config.debug.directTextureOutputTextureIndex)
        } else {
            textureRenderer.drawToQuad(engine.window.frontBuffer, engine.deferredRenderingBuffer.finalMap)
        }
    }

    init {
        engine.renderSystems.add(this)
        SwingUtils.invokeLater {
            TimingsFrame(engine)
            ribbon.setApplicationMenuCommand(ApplicationMenu(engine))

            addTask(ViewTask(engine, config, this))
            addTask(SceneTask(engine))
            addTask(TransformTask(this))
            addTask(TextureTask(engine, editor))
            addTask(MaterialTask(engine, editor, selectionSystem))
            showConfigFrame()
        }

        MouseInputProcessor(engine, selectionSystem::selection, this).apply {
            editor.canvas.addMouseMotionListener(this)
            editor.canvas.addMouseListener(this)
        }

        engine.managers.register(EditorManager(this))

        engine.renderSystems.add(this)
    }

    val keyLogger = KeyLogger().apply {
        editor.addKeyListener(this)
    }
    fun isKeyPressed(key: Int) = keyLogger.pressedKeys.contains(key)

    private fun showConfigFrame() = SwingUtils.invokeLater  {
        ConfigFrame(engine, config, editor)
    }

    fun addTask(task: RibbonTask) = ribbon.addTask(task)

    companion object {

        fun getResizableIconFromSvgResource(resource: String): ResizableIcon {
            return SvgBatikResizableIcon.getSvgIcon(RibbonEditor::class.java.classLoader.getResource(resource), Dimension(24, 24))
        }

        fun getResizableIconFromImageSource(resource: String): ResizableIcon {
            return ImageWrapperResizableIcon.getIcon(RibbonEditor::class.java.classLoader.getResource(resource), Dimension(24, 24))
        }

        fun getResizableIconFromImageSource(image: Image): ResizableIcon {
            return ImageWrapperResizableIcon.getIcon(image, Dimension(24, 24))
        }
    }
}