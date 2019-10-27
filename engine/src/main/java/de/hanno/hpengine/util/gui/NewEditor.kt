package de.hanno.hpengine.editor

import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.swing.MigLayout
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
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
import java.awt.Graphics
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_A
import java.awt.event.KeyEvent.VK_D
import java.awt.event.KeyEvent.VK_E
import java.awt.event.KeyEvent.VK_Q
import java.awt.event.KeyEvent.VK_S
import java.awt.event.KeyEvent.VK_SHIFT
import java.awt.event.KeyEvent.VK_W
import java.awt.event.KeyEvent.VK_X
import java.awt.event.KeyEvent.VK_Y
import java.awt.event.KeyEvent.VK_Z
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.BorderFactory
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.experimental.and
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty0


class NewEditor(val engine: EngineImpl) : JRibbonFrame("HPEngine"), RenderSystem {

    val finalTexture = engine.deferredRenderingBuffer.finalBuffer.textures.first()
    val image = BufferedImage(finalTexture.dimension.width, finalTexture.dimension.height, BufferedImage.TYPE_INT_ARGB)


    val sidePanel = JPanel().apply {
        layout = MigLayout("wrap 1")
        background = Color.BLACK
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)
    }

    val mainPanel = JPanel()

    val pressedKeys = mutableSetOf<Int>()
    fun isKeyPressed(key: Int) = pressedKeys.contains(key)

    var selectedEntity: Entity? = null
    var mouseClicked: MouseEvent? = null

    var xSelected = false
    var ySelected = false
    var zSelected = false

    init {

        val mouseMotionListener1 = MouseMotionListener1(engine, ::selectedEntity, this)
        mainPanel.addMouseMotionListener(mouseMotionListener1)
        mainPanel.addMouseListener(mouseMotionListener1)
        mainPanel.addMouseListener(object : MouseListener {
            override fun mouseReleased(e: MouseEvent?) {}

            override fun mouseEntered(e: MouseEvent?) {}

            override fun mouseClicked(e: MouseEvent) {
                mouseClicked = e
            }

            override fun mouseExited(e: MouseEvent?) {}

            override fun mousePressed(e: MouseEvent?) {}

        })

        addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent) { }

            override fun keyPressed(e: KeyEvent) {
                pressedKeys.add(e.keyCode)
                if(e.keyCode == VK_X) {
                    xSelected = !xSelected
                } else if(e.keyCode == VK_Y) {
                    ySelected = !ySelected
                } else if(e.keyCode == VK_Z) {
                    zSelected = !zSelected
                }
            }

            override fun keyReleased(e: KeyEvent) {
                pressedKeys.remove(e.keyCode)
            }
        })
        isFocusable = true
        focusTraversalKeysEnabled = false

        engine.managers.register(NewEditorManager(this))
        engine.renderSystems.add(object : RenderSystem {
            val floatBuffer = BufferUtils.createFloatBuffer(4)
            override fun render(result: DrawResult, state: RenderState) {

                mouseClicked?.let { event ->
                    engine.deferredRenderingBuffer.use(engine.gpuContext, false)
                    engine.gpuContext.readBuffer(4)
                    floatBuffer.rewind()
                    val ratio = Vector2f(mainPanel.width.toFloat() / engine.gpuContext.window.width.toFloat(),
                            mainPanel.height.toFloat() / engine.gpuContext.window.height.toFloat())
                    val adjustedX = (event.x * ratio.x).toInt()
                    val adjustedY = (event.y * ratio.y).toInt()
                    GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL_RGBA, GL11.GL_FLOAT, floatBuffer)
                    val entityIndex = floatBuffer.get()
                    val pickedEntity = engine.scene.getEntities()[entityIndex.toInt()]
                    if(selectedEntity == null) {
                        selectEntity(pickedEntity)
                    } else if(selectedEntity?.name == pickedEntity.name) {
                        unselectEntity()
                    }

                    println("Selected $selectedEntity")
                    mouseClicked = null
                }
            }

            private fun selectEntity(pickedEntity: Entity) = SwingUtils.invokeAndWait {
                selectedEntity = pickedEntity
                sidePanel.removeAll()
                sidePanel.add(JButton("Unselect").apply {
                    addActionListener {
                        unselectEntity()
                    }
                })
                pickedEntity.isSelected = true
                sidePanel.add(EntityGrid(pickedEntity))
                sidePanel.revalidate()
                sidePanel.repaint()
            }

            private fun unselectEntity() = SwingUtils.invokeLater {
                selectedEntity?.isSelected = false
                selectedEntity = null
                sidePanel.removeAll()
                sidePanel.revalidate()
                sidePanel.repaint()
            }
        })
    }

    val imageLabel = ImageLabel(ImageIcon(image))

    inner class ImageLabel(image: Icon) : JLabel(image) {
        override fun getPreferredSize(): Dimension {
            return Dimension(mainPanel.width, mainPanel.height)
        }

        override fun paint(g: Graphics) {
            g.drawImage(image, 0, 0, this.width, this.height, null)
        }
    }

    init {

        add(sidePanel, BorderLayout.LINE_END)
        mainPanel.add(imageLabel)

        engine.renderSystems.add(this)
        setDefaultLookAndFeelDecorated(true)
        this.size = Dimension(1280, 720)

        val newEntityBand = JRibbonBand("New", null).apply {
            val command = Command.builder()
                    .setText("Entity")
                    .setIconFactory { getResizableIconFromResource("3d_rotation-24px.svg") }
                    .setAction { println("Entity created!") }
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

        val entityTask = RibbonTask("Entity", newEntityBand)

        val translateBand = JFlowRibbonBand("Active Axes", null).apply {
            resizePolicies = listOf(CoreRibbonResizePolicies.FlowTwoRows(this))

            val translateAxisToggleGroup = CommandToggleGroupModel();

            val commands = listOf(Pair("X", ::xSelected), Pair("Y", ::ySelected), Pair("Z", ::zSelected)).map {
                Command.builder()
                    .setToggle()
                    .setText(it.first)
                    .setIconFactory { getResizableIconFromResource("3d_rotation-24px.svg") }
                    .inToggleGroup(translateAxisToggleGroup)
                    .setAction { event ->
                        it.second.set(!it.second.get())
                        event.command.isToggleSelected = it.second.get()
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

        addTask(entityTask)
        addTask(transformTask)

        defaultCloseOperation = EXIT_ON_CLOSE
        isVisible = true


        add(mainPanel, BorderLayout.CENTER)

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
            return SvgBatikResizableIcon.getSvgIcon(NewEditor::class.java.classLoader.getResource(resource), Dimension(24, 24))
        }
    }

}

class EntityGrid(val entity: Entity): JPanel() {
    init {
        layout = MigLayout("wrap 2")
        labeled("Visible", entity::isVisible.toCheckBox())
        labeled("Name", entity::name.toTextField())
    }

    private fun KMutableProperty<Boolean>.toCheckBox(): JCheckBox {
        return JCheckBox(name).apply {
            isSelected = this@toCheckBox.getter.call()
            addActionListener { this@toCheckBox.setter.call(isSelected) }
        }
    }
    private fun KMutableProperty<String>.toTextField(): JTextField {
        return JTextField(name).apply {
            text = this@toTextField.getter.call()
            addActionListener { this@toTextField.setter.call(text) }
        }
    }

    private fun labeled(label: String, component: JComponent) {
        add(JLabel(label))
        add(component)
    }
}


class NewEditorManager(val editor: NewEditor) : Manager {
    override fun CoroutineScope.update(deltaSeconds: Float) {

        val turbo = if (editor.isKeyPressed(VK_SHIFT)) 3f else 1f

        val moveAmount = 100f * 0.1f * deltaSeconds * turbo

        val entity = editor.engine.scene.camera.entity

        if (editor.isKeyPressed(VK_W)) {
            entity.translate(Vector3f(0f, 0f, -moveAmount))
        }
        if (editor.isKeyPressed(VK_S)) {
            entity.translate(Vector3f(0f, 0f, moveAmount))
        }
        if (editor.isKeyPressed(VK_A)) {
            entity.translate(Vector3f(-moveAmount, 0f, 0f))
        }
        if (editor.isKeyPressed(VK_D)) {
            entity.translate(Vector3f(moveAmount, 0f, 0f))
        }
        if (editor.isKeyPressed(VK_Q)) {
            entity.translate(Vector3f(0f, -moveAmount, 0f))
        }
        if (editor.isKeyPressed(VK_E)) {
            entity.translate(Vector3f(0f, moveAmount, 0f))
        }
    }
}

class MouseMotionListener1(val engine: Engine<*>, val selectedEntity: KProperty0<Entity?>, val editor: NewEditor) : MouseAdapter() {
    private var lastX: Float? = null
    private var lastY: Float? = null

    private var pitch = 0f
    private var yaw = 0f

    override fun mouseMoved(e: MouseEvent) { }
    override fun mousePressed(e: MouseEvent) {
        lastX = e.x.toFloat()
        lastY = e.y.toFloat()
    }

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 10.1f * 0.05 * rotationDelta

        val deltaX = (lastX ?: e.x.toFloat()) - e.x.toFloat()
        val deltaY = (lastY ?: e.y.toFloat()) - e.y.toFloat()
        println("DeltaX $deltaX, DeltaY $deltaY")

        val pitchAmount = Math.toRadians((deltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((-deltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val entityOrNull = selectedEntity.call()
        if(entityOrNull == null) {
            val entity = engine.scene.camera.entity
            val oldTranslation = entity.getTranslation(Vector3f())
            entity.setTranslation(Vector3f(0f, 0f, 0f))
            entity.rotationX(pitchAmount.toFloat())
            entity.rotateLocalY((-yawAmount).toFloat())
            entity.translateLocal(oldTranslation)
        } else {
            val turbo = if (editor.isKeyPressed(VK_SHIFT)) 3f else 1f

            val moveAmountX = deltaX * turbo
            val moveAmountY = deltaY * turbo
            println("MoveAmountX $moveAmountX")
            if(editor.xSelected) {
                entityOrNull.translation(Vector3f(moveAmountX, 0f, 0f))
            }
            if(editor.ySelected) {
                entityOrNull.translation(Vector3f(0f, moveAmountY, 0f))
            }
            if(editor.zSelected) {
                entityOrNull.translation(Vector3f(0f, 0f, moveAmountY))
            }
        }
    }

}