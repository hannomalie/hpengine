package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.manager.Manager
import kotlinx.coroutines.CoroutineScope
import org.joml.Vector3f
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
import org.pushingpixels.flamingo.api.common.RichTooltip
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon
import org.pushingpixels.flamingo.api.common.model.Command
import org.pushingpixels.flamingo.api.common.model.CommandButtonPresentationModel
import org.pushingpixels.flamingo.api.ribbon.JRibbonBand
import org.pushingpixels.flamingo.api.ribbon.JRibbonFrame
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.flamingo.api.ribbon.resize.CoreRibbonResizePolicies
import org.pushingpixels.neon.icon.ResizableIcon
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
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.swing.BorderFactory
import javax.swing.GroupLayout
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import kotlin.experimental.and


class NewEditor(val engine: EngineImpl) : JRibbonFrame("HPEngine"), RenderSystem {

    val finalTexture = engine.deferredRenderingBuffer.finalBuffer.textures.first()
    val image = BufferedImage(finalTexture.dimension.width, finalTexture.dimension.height, BufferedImage.TYPE_INT_ARGB)


    val sidePanel = JPanel().apply {
        add(
            JScrollPane(JPanel().apply {
                layout = GroupLayout(this).apply {
                    val button = JButton("button")
                    val label = JLabel("label")

                    setHorizontalGroup(
                            createSequentialGroup()
                                    .addComponent(label)
                                    .addComponent(button)
                                    .addGroup(createParallelGroup(GroupLayout.Alignment.CENTER)
                                            .addComponent(label)
                                            .addComponent(button))
                    )

                    setVerticalGroup(
                            createSequentialGroup()
                                    .addGroup(createParallelGroup(GroupLayout.Alignment.CENTER)
                                            .addComponent(label)
                                            .addComponent(button)
                                            .addComponent(label))
                                    .addComponent(button)
                    )
                    autoCreateGaps = true;
                    autoCreateContainerGaps = true;
                }

                preferredSize = Dimension(400, 100)
                background = Color.BLACK
            }).apply {
                verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
            }
        )
        background = Color.BLACK
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Color.BLACK)
    }

    val mainPanel = JPanel().apply {
        //        layout = FlowLayout()
//        add(JButton("button"))
//        add(JCheckBox("check"))
//        add(JLabel("label"))
//        preferredSize = Dimension(200, 100)
//            background = Color.BLACK
    }

    val pressedKeys = mutableSetOf<Int>()
    fun isKeyPressed(key: Int) = pressedKeys.contains(key)

    init {

        mainPanel.addMouseMotionListener(MouseMotionListener1(mainPanel, engine))

        addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent) {}

            override fun keyPressed(e: KeyEvent) {
                pressedKeys.add(e.keyCode)
            }

            override fun keyReleased(e: KeyEvent) {
                pressedKeys.remove(e.keyCode)
            }

        })
        isFocusable = true
        focusTraversalKeysEnabled = false

        engine.managers.register(NewEditorManager(this))
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


//        val configBand = JFlowRibbonBand("XXX",
//                ic_create_new_folder_black_24px.factory(),
//                CommandAction { e -> println(e) })
//        val config = RibbonTask("Config", configBand)
//        ribbon.addTask(config)


        val newEntityBand = JRibbonBand("New", null)

        val command = Command.builder()
                .setText("Entity")
                .setIconFactory { getResizableIconFromResource("3d_rotation-24px.svg") }
                .setAction { println("Entity created!") }
                .setActionRichTooltip(RichTooltip.builder()
                        .setTitle("Entity")
                        .addDescriptionSection("Creates an entity")
                        .build())
                .build()
        newEntityBand.addRibbonCommand(command.project(CommandButtonPresentationModel.builder()
                .setPopupKeyTip("X")
                .setTextClickAction()
                .build()), JRibbonBand.PresentationPriority.TOP)
        newEntityBand.resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(newEntityBand), CoreRibbonResizePolicies.Mid2Low(newEntityBand))
        val entityTask = RibbonTask("Entity", newEntityBand)

        val band2 = JRibbonBand("world!", null)
        band2.resizePolicies = listOf(CoreRibbonResizePolicies.Mirror(band2), CoreRibbonResizePolicies.Mid2Low(band2))
        val task2 = RibbonTask("Two", band2)

        addTask(entityTask)
        addTask(task2)

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
            return ImageWrapperResizableIcon.getIcon(NewEditor::class.java.classLoader.getResource(resource), Dimension(24, 24))
        }
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

class MouseMotionListener1(mainPanel: JPanel, val engine: Engine<*>) : MouseMotionListener {
    private var lastDeltaX = 0f
    private var lastDeltaY = 0f
    private var lastX = mainPanel.width.toFloat() / 2f
    private var lastY = mainPanel.height.toFloat() / 2f

    private var pitch = 0f
    private var yaw = 0f

    override fun mouseMoved(e: MouseEvent) {}

    override fun mouseDragged(e: MouseEvent) {
        val rotationDelta = 10f
        val rotationAmount = 10.1f * 0.05 * rotationDelta
        val currentDeltaX = lastX - e.x.toFloat()
        val currentDeltaY = lastY - e.y.toFloat()
        val smoothDeltaX = 0.5f * (currentDeltaX + lastDeltaX)
        val smoothDeltaY = 0.5f * (currentDeltaY + lastDeltaY)

        lastX = e.x.toFloat()
        lastY = e.y.toFloat()
        lastDeltaX = currentDeltaX
        lastDeltaY = currentDeltaY

        val entity = engine.scene.camera.entity

        val pitchAmount = Math.toRadians((smoothDeltaY * rotationAmount % 360))
        val yawAmount = Math.toRadians((-smoothDeltaX * rotationAmount % 360))

        yaw += yawAmount.toFloat()
        pitch += pitchAmount.toFloat()

        val oldTranslation = entity.getTranslation(Vector3f())
        entity.setTranslation(Vector3f(0f, 0f, 0f))
        entity.rotateLocalY((-yawAmount).toFloat())
        entity.rotateX(pitchAmount.toFloat())
        entity.translateLocal(oldTranslation)
    }

}