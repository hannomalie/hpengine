package de.hanno.hpengine.editor

import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.SimpleConfig
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.texture.FileBasedTexture2D
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.SimpleScene
import de.hanno.hpengine.engine.transform.SimpleTransform
import de.hanno.hpengine.util.gui.DirectTextureOutputItem
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import org.joml.Vector3f
import org.joml.Vector4f
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
import org.pushingpixels.flamingo.api.ribbon.RibbonApplicationMenu
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryContentModel
import org.pushingpixels.flamingo.api.ribbon.model.RibbonGalleryPresentationModel
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonApplicationMenuCommandButtonProjection
import org.pushingpixels.flamingo.api.ribbon.projection.RibbonGalleryProjection
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.flamingo.api.ribbon.synapse.model.ComponentPresentationModel
import org.pushingpixels.flamingo.api.ribbon.synapse.model.RibbonDefaultComboBoxContentModel
import org.pushingpixels.flamingo.api.ribbon.synapse.projection.RibbonComboBoxProjection
import org.pushingpixels.neon.icon.ResizableIcon
import org.pushingpixels.photon.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.function.Consumer
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.tree.DefaultMutableTreeNode

class RibbonEditor(val engine: EngineImpl, val config: SimpleConfig) : JRibbonFrame("HPEngine"), RenderSystem {
    private val lineRenderer = LineRendererImpl(engine)
    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }

    val appMenuNew = Command.builder()
        .setText("New Scene")
        .setIconFactory { getResizableIconFromSvgResource("create_new_folder-24px.svg") }
        .setExtraText("Creates an empty scene")
        .setAction { engine.sceneManager.scene = SimpleScene("Scene_${System.currentTimeMillis()}", engine) }
        .build()
    val applicationMenu = RibbonApplicationMenu(CommandGroup(appMenuNew))

    val ribbonMenuCommandProjection = RibbonApplicationMenuCommandButtonProjection(
        Command.builder()
            .setText("Application")
            .setSecondaryContentModel(applicationMenu)
            .build(), CommandButtonPresentationModel.builder().build()).apply {

        ribbon.setApplicationMenuCommand(this)
    }

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
    val sceneTree = SceneTree(engine, this).apply {
        var selectedTreeElement: Any? = null

        fun handleContextMenu(mouseEvent: MouseEvent) {

            if(mouseEvent.button == MouseEvent.BUTTON1) {

                when(val selection = selectedTreeElement) {
                    is Camera -> {
                        sidePanel.doWithRefresh {
                            add(JButton("Unselect").apply {
                                addActionListener {
                                    sidePanel.removeAll()
                                }
                            })
                            add(CameraGrid(selection))
                        }
                    }
                }
            } else if (mouseEvent.isPopupTrigger) {

                when(val selection = selectedTreeElement) {
                    is Entity -> {
                        JPopupMenu().apply {
                            add(
                                JMenu("Add").apply {
                                    add(JMenuItem("ModelComponent").apply {
                                        addActionListener {
                                            JFileChooser(engine.directories.gameDir).apply {
                                                if(showOpenDialog(this@RibbonEditor) == JFileChooser.APPROVE_OPTION) {
                                                    GlobalScope.launch {
                                                        val loadedModels = LoadModelCommand(selectedFile,
                                                                "Model_${System.currentTimeMillis()}",
                                                                engine.materialManager,
                                                                engine.directories.gameDir,
                                                                selection).execute()
                                                        engine.singleThreadContext.launch {
                                                            with(engine.scene) {
                                                                addAll(loadedModels.entities)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    })
                                    add(JMenu("Light").apply {
                                        add(JMenuItem("PointLight").apply {
                                            addActionListener {
                                                val component = PointLight(selection, Vector4f(1f, 1f, 1f, 1f), 10f)
                                                engine.singleThreadContext.launch {
                                                    selection.addComponent(component)

                                                    with(engine.managers) {
                                                        onComponentAdded(component)
                                                    }
                                                }
                                            }
                                        })
                                    })
                                }
                            )
                            show(mouseEvent.component, mouseEvent.x, mouseEvent.y)
                        }
                    }
                }
            }
        }

        val mouseListener = object: MouseAdapter() {
            override fun mousePressed(mouseEvent: MouseEvent) {
                val row = getClosestRowForLocation(mouseEvent.x, mouseEvent.y)
                setSelectionRow(row)
                selectedTreeElement = (lastSelectedPathComponent as DefaultMutableTreeNode).userObject

                handleContextMenu(mouseEvent)
            }

            override fun mouseReleased(mouseEvent: MouseEvent) {
                handleContextMenu(mouseEvent)
            }
        }

        addMouseListener(mouseListener);
    }

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
    var transformMode = TransformMode.None
    var transformSpace = TransformSpace.World
    var selectionMode = SelectionMode.Entity

    val entitySelector = EntitySelector(this).apply {
        engine.renderSystems.add(this)
    }

    init {
        MouseInputProcessor(engine, entitySelector::selection, this).apply {
            mainPanel.addMouseMotionListener(this)
            mainPanel.addMouseListener(this)
        }

        isFocusable = true
        focusTraversalKeysEnabled = false

        engine.managers.register(EditorManager(this))

        add(sidePanel, BorderLayout.LINE_END)

        engine.renderSystems.add(this)
        this.size = Dimension(1280, 720)

        addViewTask()
        addSceneTask()
        addTransformTask()
        addTextureTask()
        addConfigAnchoredCommand()

        defaultCloseOperation = EXIT_ON_CLOSE
        isVisible = true

    }

    private fun addViewTask() {

        val outputBand = JRibbonBand("Output", null).apply {

            val command = Command.builder()
                    .setText("Direct texture output")
                    .setToggle()
                    .setIconFactory { getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        config.debug.isUseDirectTextureOutput = it.command.isToggleSelected
                    }
                    .build()
            addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                    .setTextClickAction()
                    .build()), JRibbonBand.PresentationPriority.TOP)

            resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(this), CoreRibbonResizePolicies.Mid2Low(this))
        }


        val outputIndexBand = JFlowRibbonBand("Output", null).apply {
            val renderTargetTextures = ArrayList<DirectTextureOutputItem>()
            for (target in engine.gpuContext.registeredRenderTargets) {
                for (i in 0 until target.textures.size) {
                    val name = target.name + " - " + i // TODO: Revive names here
                    renderTargetTextures.add(DirectTextureOutputItem(target, name, target.getRenderedTexture(i)))
                }
            }

            val directTextureOutputComboBoxModel = RibbonDefaultComboBoxContentModel.builder<DirectTextureOutputItem>()
                    .setItems(renderTargetTextures.toTypedArray())
                    .build()

            directTextureOutputComboBoxModel.addListDataListener(object : ListDataListener {
                override fun intervalRemoved(e: ListDataEvent?) {}
                override fun intervalAdded(e: ListDataEvent?) {}

                override fun contentsChanged(e: ListDataEvent) {
                    val newSelection = directTextureOutputComboBoxModel.selectedItem as DirectTextureOutputItem
                    config.debug.directTextureOutputTextureIndex = newSelection.textureId
                }
            })

            addFlowComponent(RibbonComboBoxProjection(directTextureOutputComboBoxModel, ComponentPresentationModel.builder().build()))

        }

        val selectionModeBand = JFlowRibbonBand("Selection Mode", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val selectionModeToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(SelectionMode.Entity, ::selectionMode),
                    Pair(SelectionMode.Mesh, ::selectionMode)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(selectionMode == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(selectionModeToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(SelectionMode.Entity) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val selectionModeCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(selectionModeCommandGroupProjection)
        }

        val viewTask = RibbonTask("Viewport", outputBand, outputIndexBand, selectionModeBand)

        addTask(viewTask)
    }

    private fun addTransformTask() {

        val activeAxesBand = JFlowRibbonBand("Active Axes", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformAxisToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(AxisConstraint.X, ::constraintAxis),
                    Pair(AxisConstraint.Y, ::constraintAxis),
                    Pair(AxisConstraint.Z, ::constraintAxis)).map {
                Command.builder()
                        .setToggle()
                        .setText(it.first.toString())
                        .setToggleSelected(constraintAxis == it.first)
                        .setIconFactory { getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformAxisToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(AxisConstraint.None) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val translateCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(translateCommandGroupProjection)
        }

        val transformModeBand = JFlowRibbonBand("Transform Mode", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformModeToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(TransformMode.Translate, ::transformMode),
                    Pair(TransformMode.Rotate, ::transformMode),
                    Pair(TransformMode.Scale, ::transformMode)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(transformMode == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformModeToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(TransformMode.None) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val transformModeCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(transformModeCommandGroupProjection)
        }

        val transformSpaceBand = JFlowRibbonBand("Transform Space", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val transformSpaceToggleGroup = CommandToggleGroupModel()

            val commands = listOf(
                    Pair(TransformSpace.World, ::transformSpace),
                    Pair(TransformSpace.Local, ::transformSpace),
                    Pair(TransformSpace.View, ::transformSpace)).map {
                Command.builder()
                        .setToggle()
                        .setToggleSelected(transformSpace == it.first)
                        .setText(it.first.toString())
                        .setIconFactory { getResizableIconFromSvgResource("3d_rotation-24px.svg") }
                        .inToggleGroup(transformSpaceToggleGroup)
                        .setAction { event ->
                            if (it.second.get() == it.first) it.second.set(TransformSpace.World) else it.second.set(it.first)
                            event.command.isToggleSelected = it.second.get() == it.first
                        }
                        .build()
            }
            val transformSpaceCommandGroupProjection = CommandStripProjection(CommandGroup(commands),
                    CommandStripPresentationModel.builder()
                            .setCommandPresentationState(CommandButtonPresentationState.MEDIUM)
                            .build())
            addFlowComponent(transformSpaceCommandGroupProjection)
        }

        val transformTask = RibbonTask("Transform", activeAxesBand, transformModeBand, transformSpaceBand)

        addTask(transformTask)
    }

    private fun addSceneTask() {
        val entityBand = JRibbonBand("Entity", null).apply {
            val command = Command.builder()
                    .setText("Create")
                    .setIconFactory { getResizableIconFromSvgResource("add-24px.svg") }
                    .setAction {
                        engine.singleThreadContext.launch {
                            with(engine.sceneManager)  {
                                add(Entity("NewEntity_${engine.scene.getEntities().count { it.name.startsWith("NewEntity") }}"))
                            }
                        }
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

    private fun addConfigAnchoredCommand() {
        val command = Command.builder()
                .setText("Show Config")
                .setToggle()
                .setIconFactory { getResizableIconFromSvgResource("settings_applications-24px.svg") }
                .setAction { event ->
                    if (event.command.isToggleSelected) {
                        mainPanel.setContent(
                                ReloadableScrollPane(ConfigGrid(config, engine.eventBus)).apply {
                                    this.preferredSize = Dimension(mainPanel.width, mainPanel.height)
                                }
                        )
                    } else {
                        mainPanel.setContent(imageLabel)
                    }
                }.build()

        ribbon.addAnchoredCommand(command.project(CommandButtonPresentationModel.builder()
                .setTextClickAction()
                .build()))

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
                    .setIconFactory { getResizableIconFromSvgResource("refresh-24px.svg") }
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

    override fun afterFrameFinished() {
        bufferImage()
        imageLabel.repaint()
    }

    override fun render(result: DrawResult, state: RenderState) {
        engine.deferredRenderingBuffer.gBuffer.use(engine.gpuContext, false)

        fun HpVector4f.toJomlVec3() = Vector3f(x, y, z)
        val vertexArray = state.vertexIndexBufferStatic.vertexStructArray
        val indexArray = state.vertexIndexBufferStatic.indexBuffer
        val asIntBuffer = indexArray.buffer.asIntBuffer()
//        (0 until asIntBuffer.capacity()).forEach { index ->
//            lineRenderer.batchPointForLine(vertexArray[asIntBuffer[index]].position.toJomlVec3())
//        }
//        lineRenderer.drawAllLines(2f, Consumer { program ->
//            program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
//            program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
//            program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
//            program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
//        })
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

//            GlobalScope.launch() {
                image.setData(width, height, channels, buffer)
//            }
        }
    }

    fun BufferedImage.setData(width: Int, height: Int, channels: Int, buffer: ByteBuffer) {
        for (y in 0 until height) {
            for (x in 0 until width) {

                val i = (x + y * width) * channels

                val r = buffer.get(i).toUByte() and 0xFF.toUByte()
                val g = buffer.get(i + 1).toUByte() and 0xFF.toUByte()
                val b = buffer.get(i + 2).toUByte() and 0xFF.toUByte()
                val a = if (channels == 4) (buffer.get(i + 3).toUByte() and 0xFF.toUByte()) else 255.toUByte()

                val rgb = (a.toInt() shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

                val mirroredY = height - 1 - y
                setRGB(x, mirroredY, rgb)
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



enum class AxisConstraint(val axis: Vector3f) {
    None(Vector3f()),
    X(Vector3f(1f, 0f, 0f)),
    Y(Vector3f(0f, 1f, 0f)),
    Z(Vector3f(0f, 0f, 1f))
}
enum class TransformMode {
    None, Translate, Rotate, Scale
}

enum class TransformSpace {
    World, Local, View
}

enum class SelectionMode {
    Entity, Mesh
}

fun JPanel.doWithRefresh(addContent: JPanel.() -> Unit) {
    SwingUtils.invokeLater {
        removeAll()
        addContent()
        revalidate()
        repaint()
    }
}
