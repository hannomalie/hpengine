package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import net.miginfocom.swing.MigLayout
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_RGBA8
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT
import org.lwjgl.opengl.GL11.GL_TEXTURE_INTERNAL_FORMAT
import org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.glGetTexImage
import org.lwjgl.opengl.GL11.glGetTexLevelParameteri
import org.pushingpixels.flamingo.api.common.CommandButtonPresentationState
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandGroup
import org.pushingpixels.flamingo.api.common.model.CommandStripPresentationModel
import org.pushingpixels.flamingo.api.common.model.CommandToggleGroupModel
import org.pushingpixels.flamingo.api.common.projection.CommandStripProjection
import org.pushingpixels.flamingo.api.ribbon.JFlowRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.neon.icon.ResizableIcon
import org.pushingpixels.photon.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JPanel
import kotlin.experimental.and


class RibbonEditor(val engine: EngineImpl) : JRibbonFrame("HPEngine"), RenderSystem {

    val finalTexture = engine.deferredRenderingBuffer.finalBuffer.textures.first()
    val image = BufferedImage(finalTexture.dimension.width, finalTexture.dimension.height, BufferedImage.TYPE_INT_ARGB)

    val sidePanel = JPanel().apply {
        layout = MigLayout("wrap 1")
        background = Color.BLACK
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)
    }

    val mainPanel = MainPanel().apply {
        this@RibbonEditor.add(this, BorderLayout.CENTER)
    }
    val sceneTree = SceneTree(engine, this)
    val sceneTreePanel = ReloadableScrollPane(sceneTree).apply {
        this.preferredSize = Dimension(300, mainPanel.height)

        this@RibbonEditor.add(this, BorderLayout.LINE_START)
    }
    val imageLabel = ImageLabel(ImageIcon(image), this)

    val keyLogger = KeyLogger().apply {
        addKeyListener(this)
    }
    fun isKeyPressed(key: Int) = keyLogger.pressedKeys.contains(key)

    var constraintAxis = AxisConstraint.None

    val entitySelector = EntitySelector(this).apply {
        engine.renderSystems.add(this)
    }

    init {
        MouseInputProcessor(engine, entitySelector::selectedEntity, this).apply {
            mainPanel.addMouseMotionListener(this)
            mainPanel.addMouseListener(this)
        }

        isFocusable = true
        focusTraversalKeysEnabled = false

        engine.managers.register(EditorManager(this))

        add(sidePanel, BorderLayout.LINE_END)
        mainPanel.add(imageLabel)

        engine.renderSystems.add(this)
        setDefaultLookAndFeelDecorated(true)
        this.size = Dimension(1280, 720)

        val entityBand = JRibbonBand("Entity", null).apply {
            val command = Command.builder()
                .setText("Create")
                .setIconFactory { getResizableIconFromResource("add-24px.svg") }
                .setAction {
                    engine.sceneManager.add(Entity("NewEntity_${engine.scene.getEntities().count { it.name.startsWith("NewEntity")}}"))
                }
                .setActionRichTooltip(RichTooltip.builder()
                        .setTitle("Entity")
                        .addDescriptionSection("Creates an entity")
                        .build())
                .build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                .setTextClickAction()
                .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        val sceneTask = RibbonTask("Scene", entityBand)

        val translateBand = JFlowRibbonBand("Active Axes", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val translateAxisToggleGroup = CommandToggleGroupModel();

            val commands = listOf(Pair(AxisConstraint.X, ::constraintAxis), Pair(AxisConstraint.Y, ::constraintAxis), Pair(AxisConstraint.Z, ::constraintAxis)).map {
                Command.builder()
                    .setToggle()
                    .setText(it.first.toString())
                    .setIconFactory { getResizableIconFromResource("3d_rotation-24px.svg") }
                    .inToggleGroup(translateAxisToggleGroup)
                    .setAction { event ->
                        if(it.second.get() == it.first) it.second.set(AxisConstraint.None) else it.second.set(it.first)
                        event.command.isToggleSelected = it.second.get() == it.first
                    }
                    .build()
            }
            val translateCommandGroup = CommandGroup(commands)
            val projection = CommandStripProjection(translateCommandGroup,
                CommandStripPresentationModel.builder()
                    .setCommandPresentationState(CommandButtonPresentationState.SMALL)
                    .build())
            addFlowComponent(projection)
        }
        val transformTask = RibbonTask("Translate", translateBand)

        addTask(sceneTask)
        addTask(transformTask)

        defaultCloseOperation = EXIT_ON_CLOSE
        isVisible = true

    }

    override fun render(result: DrawResult, state: RenderState) {
        bufferImage()
        imageLabel.repaint()
    }

    fun addTask(task: RibbonTask) = ribbon.addTask(task)

    fun bufferImage() {
        return engine.gpuContext.calculate {

            engine.gpuContext.bindTexture(finalTexture)
            val format = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT)
            val width = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH)
            val height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)

            if (format != GL_RGBA8) throw IllegalStateException("Unexpected format")
            val channels = 4

            val buffer = BufferUtils.createByteBuffer(width * height * channels)

            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)


            image.setData(width, height, channels, buffer)
        }
    }

    fun BufferedImage.setData(width: Int, height: Int, channels: Int, buffer: ByteBuffer) {
        for (y in 0 until height) {
            for (x in 0 until width) {

                val i = (x + y * width) * channels

                val r = buffer.get(i) and 0xFF.toByte()
                val g = buffer.get(i + 1) and 0xFF.toByte()
                val b = buffer.get(i + 2) and 0xFF.toByte()
                val a = (if (channels == 4) (buffer.get(i + 3) and 0xFF.toByte()).toInt() else 255)

                val mirroredY = height - 1 - y
                setRGB(x, mirroredY, (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt())
            }
        }
    }

    companion object {

        fun getResizableIconFromResource(resource: String): ResizableIcon {
            return SvgBatikResizableIcon.getSvgIcon(RibbonEditor::class.java.classLoader.getResource(resource), Dimension(24, 24))
        }
    }

}


enum class AxisConstraint {
    None, X, Y, Z
}

fun JPanel.setContent(addContent: JPanel.() -> Unit) {
    removeAll()
    addContent()
    revalidate()
    repaint()
}
