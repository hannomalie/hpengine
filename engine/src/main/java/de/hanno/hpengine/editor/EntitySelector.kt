package de.hanno.hpengine.editor

import com.alee.utils.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.batchAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.FirstPassResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DrawLinesExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.Shader
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.shader.getShaderSource
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel

class EntitySelector(val engine: Engine<OpenGl>,
                     val editor: RibbonEditor,
                     val mainPanel: JPanel,
                     val sidePanel: JPanel) : RenderSystem {
    val lineRenderer = LineRendererImpl(engine)

    val simpleColorProgramStatic = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true)))
    val simpleColorProgramAnimated = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true), Define.getDefine("ANIMATED", true)))

    constructor(editor: RibbonEditor) : this(editor.engine, editor, editor.mainPanel, editor.sidePanel)

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

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }
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

        selectedEntity?.let { entity ->
            engine.gpuContext.disable(GlCap.DEPTH_TEST)
            engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
            val origin = entity.position
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.rightDirection).mul(3f)))
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.upDirection).mul(3f)))
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.viewDirection).mul(3f)))
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
            })
            val constraintAxisVectorWorld = Vector3f(editor.constraintAxis.axis)
            val constraintAxisVector = when(editor.transformSpace) {
                TransformSpace.World -> constraintAxisVectorWorld
                TransformSpace.Local -> constraintAxisVectorWorld.rotate(entity.rotation)
                TransformSpace.View -> constraintAxisVectorWorld
            }
            lineRenderer.batchLine(origin, Vector3f(origin).add(constraintAxisVector.mul(3f)))
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(1f, 0f, 1f))
            })

            lineRenderer.batchAABBLines(entity.minMaxWorld.min, entity.minMaxWorld.max)
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
            })

            state.renderBatchesStatic.filter {
                it.entityIndex == entity.index
            }.forEach {
                engine.gpuContext.enable(GlCap.BLEND)
                engine.gpuContext.blendEquation(BlendMode.FUNC_ADD)
                engine.gpuContext.blendFunc(BlendMode.Factor.ONE, BlendMode.Factor.ONE)
//                TODO: Enable as soon as builder stuff for rendertargets is fixed
//                engine.gpuContext.enable(GlCap.DEPTH_TEST)
//                engine.gpuContext.depthFunc(GlDepthFunc.EQUAL)
                simpleColorProgramStatic.use()
                simpleColorProgramStatic.bindShaderStorageBuffer(1, state.entitiesState.materialBuffer)
                simpleColorProgramStatic.bindShaderStorageBuffer(3, state.entitiesState.entitiesBuffer)
                simpleColorProgramStatic.bindShaderStorageBuffer(6, state.entitiesState.jointsBuffer)
                simpleColorProgramStatic.setTextureUniforms(engine.gpuContext, it.materialInfo.maps)
                simpleColorProgramStatic.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                simpleColorProgramStatic.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                simpleColorProgramStatic.setUniformAsMatrix4("viewProjectionMatrix", state.camera.viewProjectionMatrixAsBuffer)
                draw(state.vertexIndexBufferStatic.vertexBuffer,
                     state.vertexIndexBufferStatic.indexBuffer, it, simpleColorProgramStatic, false, false)
            }
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