package de.hanno.hpengine.editor

import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import org.joml.Vector2f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JPanel

class EntitySelector(val engine: Engine<OpenGl>, val mainPanel: JPanel, val sidePanel: JPanel) : RenderSystem {
    constructor(editor: RibbonEditor): this(editor.engine, editor.mainPanel, editor.sidePanel)

    init {
        mainPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                mouseClicked = e
            }
        })
    }

    val floatBuffer = BufferUtils.createFloatBuffer(4)

    var selectedEntity: Entity? = null
    var mouseClicked: MouseEvent? = null

    override fun render(result: DrawResult, state: RenderState) {

        mouseClicked?.let { event ->
            engine.deferredRenderingBuffer.use(engine.gpuContext, false)
            engine.gpuContext.readBuffer(4)
            floatBuffer.rewind()
            val ratio = Vector2f(mainPanel.width.toFloat() / engine.gpuContext.window.width.toFloat(),
                    mainPanel.height.toFloat() / engine.gpuContext.window.height.toFloat())
            val adjustedX = (event.x * ratio.x).toInt()
            val adjustedY = (event.y * ratio.y).toInt()
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer)
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

    fun selectEntity(pickedEntity: Entity) = SwingUtils.invokeAndWait {
        selectedEntity = pickedEntity
        pickedEntity.isSelected = true
        sidePanel.doWithRefresh {
            add(JButton("Unselect").apply {
                addActionListener {
                    unselectEntity()
                }
            })
            add(EntityGrid(pickedEntity))
        }
    }

    fun unselectEntity() = SwingUtils.invokeLater {
        selectedEntity?.isSelected = false
        selectedEntity = null
        sidePanel.removeAll()
        sidePanel.revalidate()
        sidePanel.repaint()
    }
}