package de.hanno.hpengine.editor

import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.config.SimpleConfig
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
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
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon
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
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryContentModel
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryPresentationModel
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonGalleryProjection
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.neon.icon.ResizableIcon
import org.pushingpixels.photon.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JFileChooser
import javax.swing.JLabel
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
    val imageLabel = ImageLabel(ImageIcon(image), this).apply {
        mainPanel.setContent(this)
    }

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

        engine.renderSystems.add(this)
        setDefaultLookAndFeelDecorated(true)
        this.size = Dimension(1280, 720)

        addSceneTask()
        addTransformTask()
        addTextureTask()
        addConfigTask()

        defaultCloseOperation = EXIT_ON_CLOSE
        isVisible = true

    }

    private fun addTransformTask() {
        val translateBand = JFlowRibbonBand("Active Axes", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val translateAxisToggleGroup = CommandToggleGroupModel();

            val commands = listOf(Pair(AxisConstraint.X, ::constraintAxis), Pair(AxisConstraint.Y, ::constraintAxis), Pair(AxisConstraint.Z, ::constraintAxis)).map {
                Command.builder()
                    .setToggle()
                    .setText(it.first.toString())
                    .setIconFactory { getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                    .inToggleGroup(translateAxisToggleGroup)
                    .setAction { event ->
                        if (it.second.get() == it.first) it.second.set(AxisConstraint.None) else it.second.set(it.first)
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

        addTask(transformTask)
    }

    private fun addSceneTask() {
        val entityBand = JRibbonBand("Entity", null).apply {
            val command = Command.builder()
                    .setText("Create")
                    .setIconFactory { getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        engine.sceneManager.add(Entity("NewEntity_${engine.scene.getEntities().count { it.name.startsWith("NewEntity") }}"))
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
        addTask(sceneTask)
    }

    private fun addConfigTask() {
        val configBand = JRibbonBand("Config", null).apply {
            val command = Command.builder()
                    .setText("Show")
                    .setToggle()
                    .setIconFactory { getResizableIconFromSvgResource("settings_applications-24px.svg") }
                    .setAction { event ->
                        if(event.command.isToggleSelected) {
                            mainPanel.setContent(
                                ReloadableScrollPane(ConfigGrid(engine.config as SimpleConfig)).apply { //TODO: Remove cast, use di
                                    this.preferredSize = Dimension(mainPanel.width, mainPanel.height)
                                }
                            )
                        } else {
                            mainPanel.setContent(imageLabel)
                        }
                    }.build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        val configTaskTask = RibbonTask("Config", configBand)
        addTask(configTaskTask)
    }

    private fun addTextureTask() {
        val textureBand = JRibbonBand("Texture", null).apply {
            val addTextureCommand = Command.builder()
                    .setText("Create")
                    .setIconFactory { getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        val fc = JFileChooser()
                        val returnVal = fc.showOpenDialog(this@RibbonEditor)
                        if (returnVal == JFileChooser.APPROVE_OPTION) {
                            val file = fc.selectedFile
                            engine.textureManager.getTexture(file.name, file = file)
                        }
                    }
                    .setActionRichTooltip(RichTooltip.builder()
                            .setTitle("Texture")
                            .addDescriptionSection("Creates a texture from the selected image")
                            .build())
                    .build()
            addRibbonCommand(addTextureCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)


            val textureCommands = retrieveTextureCommands()
            val contentModel = RibbonGalleryContentModel(ResizableIcon.Factory { getResizableIconFromSvgResource("add-24px.svg") },
                listOf(CommandGroup("Available textures", textureCommands))
            )
            val stylesGalleryVisibleCommandCounts = mapOf(
                JRibbonBand.PresentationPriority.LOW to 1,
                JRibbonBand.PresentationPriority.MEDIUM to 2,
                JRibbonBand.PresentationPriority.TOP to 3
            )

            val galleryProjection = RibbonGalleryProjection(contentModel, RibbonGalleryPresentationModel.builder()
                .setPreferredVisibleCommandCounts(stylesGalleryVisibleCommandCounts)
                .setPreferredPopupMaxVisibleCommandRows(3)
                .setPreferredPopupMaxCommandColumns(3)
                .setCommandPresentationState(JRibbonBand.BIG_FIXED_LANDSCAPE)
                .setExpandKeyTip("L")
                .build())
            addRibbonGallery(galleryProjection, JRibbonBand.PresentationPriority.TOP)

            val refreshTexturesCommand = Command.builder()
                .setText("Refresh")
                .setIconFactory { getResizableIconFromSvgResource("add-24px.svg") }
                .setAction {
                    contentModel.getCommandGroupByTitle("Available textures").apply {
                        SwingUtils.invokeLater {
                            removeAllCommands()
                            retrieveTextureCommands().forEach {
                                addCommand(it)
                            }
                        }
                    }
                }
                .setActionRichTooltip(RichTooltip.builder()
                        .setTitle("Refresh textures")
                        .addDescriptionSection("Populates the gallery with the current set of available textures")
                        .build())
                .build()
            addRibbonCommand(refreshTexturesCommand.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)
            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }

        val textureTask = RibbonTask("Texture", textureBand)
        addTask(textureTask)
    }

    private fun retrieveTextureCommands(): List<Command> {
        return engine.textureManager.textures.values.mapNotNull {
            if (it is FileBasedTexture2D) {
                val image = ImageIO.read(File(it.file.absolutePath))
                Command.builder()
                        .setText(it.file.name)
                        .setIconFactory { getResizableIconFromImageSource(image) }
                        .setToggle()
                        .build()
            } else null
        }
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


enum class AxisConstraint {
    None, X, Y, Z
}

fun JPanel.doWithRefresh(addContent: JPanel.() -> Unit) {
    SwingUtils.invokeLater {
        removeAll()
        addContent()
        revalidate()
        repaint()
    }
}
