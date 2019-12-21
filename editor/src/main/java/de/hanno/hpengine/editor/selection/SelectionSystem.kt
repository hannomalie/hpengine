package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SelectionMode
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.TransformSpace
import de.hanno.hpengine.editor.doWithRefresh
import de.hanno.hpengine.editor.grids.CameraGrid
import de.hanno.hpengine.editor.grids.DirectionalLightGrid
import de.hanno.hpengine.editor.grids.EntityGrid
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.grids.MeshGrid
import de.hanno.hpengine.editor.grids.PointLightGrid
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.renderer.batchAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.BlendMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.setTextureUniforms
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.transform.SimpleTransform
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.IllegalStateException
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel

class SelectionSystem(val editorComponents: EditorComponents) : RenderSystem {
    val engine: Engine<OpenGl> = editorComponents.engine
    val editor: RibbonEditor = editorComponents.editor
    val sidePanel: JPanel = editorComponents.editor.sidePanel
    val lineRenderer = LineRendererImpl(engine)

    val textureRenderer = SimpleTextureRenderer(engine, engine.deferredRenderingBuffer.colorReflectivenessTexture)

    val simpleColorProgramStatic = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true)))
    val simpleColorProgramAnimated = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true), Define.getDefine("ANIMATED", true)))

    init {
        editor.canvas.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                mouseClicked = e
            }
        })
    }

    val floatBuffer = BufferUtils.createFloatBuffer(4)

    var selection: Any? = null
    var mouseClicked: MouseEvent? = null

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        SimpleTransform().get(this)
    }

    data class MeshSelection(val mesh: Mesh<*>, val entity: Entity)

    override fun render(result: DrawResult, state: RenderState) {
        mouseClicked?.let { event ->
            engine.deferredRenderingBuffer.use(engine.gpuContext, false)
            engine.gpuContext.readBuffer(4)
            floatBuffer.rewind()
            val ratio = Vector2f(editor.canvas.width.toFloat() / engine.config.width.toFloat(),
                    editor.canvas.height.toFloat() / engine.config.height.toFloat())
            val adjustedX = (event.x / ratio.x).toInt()
            val adjustedY = engine.config.height - (event.y / ratio.y).toInt()
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer)

            val entityIndex = floatBuffer.get()
            val pickedEntity = engine.scene.getEntities()[entityIndex.toInt()]
            val meshIndex = floatBuffer.get(3)
            val modelComponent = pickedEntity.getComponent(ModelComponent::class.java)

            when(val selection = selection) {
                null -> when(editorComponents.selectionMode) {
                    SelectionMode.Entity -> selectEntity(pickedEntity)
                    SelectionMode.Mesh -> {
                        if(modelComponent != null) {
                            val selectedMesh = modelComponent.meshes[meshIndex.toInt()]
                            selectMesh(MeshSelection(selectedMesh, pickedEntity))
                        } else Unit
                    }
                }
                is Entity -> if(selection.name == pickedEntity.name) unselect() else selectEntity(pickedEntity)
                is MeshSelection -> {
                    val selectedMesh = modelComponent?.meshes?.get(meshIndex.toInt())
                    when {
                        selection.mesh.name == selectedMesh?.name -> {
                            unselect()
                        }
                        selectedMesh != null -> selectMesh(MeshSelection(selectedMesh, pickedEntity))
                        else -> Unit
                    }
                }
                is Material -> selectMaterial(selection)
                else -> Unit
            }.let {}
            mouseClicked = null
        }

        selection?.let { selection ->

//            TODO:MIES
            val entity = when (selection) {
                is MeshSelection -> selection.entity
                is Entity -> selection
                is PointLight -> selection.entity
                is DirectionalLight -> selection.entity
                is Camera -> selection.entity
                else -> throw IllegalStateException("Unsupported selection: $selection")
            }
            val mesh = if(selection is MeshSelection) selection.mesh else null

            engine.gpuContext.disable(GlCap.DEPTH_TEST)
            engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
            val origin = if(mesh != null) Vector3f(mesh.minMax.min).add(Vector3f(mesh.minMax.max).sub(mesh.minMax.min).mul(0.5f)) else entity.position
            val arrowLength = state.camera.getPosition().distance(origin).coerceIn(0.5f, 20f)
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.rightDirection).mul(arrowLength)))
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.upDirection).mul(arrowLength)))
            lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.viewDirection).mul(arrowLength)))
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
            })
            val constraintAxisVectorWorld = Vector3f(editorComponents.constraintAxis.axis)
            val constraintAxisVector = when(editorComponents.transformSpace) {
                TransformSpace.World -> constraintAxisVectorWorld
                TransformSpace.Local -> constraintAxisVectorWorld.rotate(entity.rotation)
                TransformSpace.View -> constraintAxisVectorWorld
            }
            lineRenderer.batchLine(origin, Vector3f(origin).add(constraintAxisVector.mul(arrowLength)))
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(1f, 0f, 1f))
            })

            if(mesh != null) {
                lineRenderer.batchAABBLines(mesh.minMax.min, mesh.minMax.max)
            } else {
                lineRenderer.batchAABBLines(entity.minMax.min, entity.minMax.max)
            }
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
            })

            state.renderBatchesStatic.filter { batch ->
                val modelComponent = entity.getComponent(ModelComponent::class.java)
                val meshFilter = mesh?.let {
                    val meshIndex = modelComponent?.meshes?.indexOf(it) ?: -100
                    batch.meshIndex == meshIndex
                } ?: true

                batch.entityIndex == entity.index && meshFilter
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
                simpleColorProgramStatic.bindShaderStorageBuffer(7, state.entitiesState.vertexIndexBufferStatic.vertexStructArray)
                simpleColorProgramStatic.setTextureUniforms(engine.gpuContext, it.materialInfo.maps)
                simpleColorProgramStatic.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                simpleColorProgramStatic.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                simpleColorProgramStatic.setUniformAsMatrix4("viewProjectionMatrix", state.camera.viewProjectionMatrixAsBuffer)
                draw(state.vertexIndexBufferStatic.vertexBuffer,
                     state.vertexIndexBufferStatic.indexBuffer, it, simpleColorProgramStatic, false, false)
            }

            textureRenderer.drawToQuad(engine.window.frontBuffer, engine.deferredRenderingBuffer.finalMap)
        }
    }

    fun selectPointLight(pickedPointLight: PointLight) {
        selection = pickedPointLight
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(PointLightGrid(pickedPointLight))
        }
    }
    fun selectEntity(pickedEntity: Entity) = SwingUtils.invokeAndWait {
        selection = pickedEntity
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(EntityGrid(pickedEntity))
        }
    }

    private fun JPanel.addUnselectButton() {
        add(JButton("Unselect").apply {
            addActionListener {
                unselect()
            }
        })
    }

    fun selectMesh(pickedMesh: MeshSelection) = SwingUtils.invokeAndWait {
        selection = pickedMesh
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(MeshGrid(pickedMesh.mesh, engine.scene.materialManager))
        }
    }

    fun selectMaterial(pickedMaterial: Material) = SwingUtils.invokeAndWait {
        selection = pickedMaterial
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(MaterialGrid(engine.textureManager, pickedMaterial))
        }
    }

    fun unselect() = SwingUtils.invokeLater {
        selection = null
        sidePanel.removeAll()
        sidePanel.revalidate()
        sidePanel.repaint()
    }

    fun selectDirectionalLight(pickedDirectionalLight: DirectionalLight) {
        selection = pickedDirectionalLight
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(DirectionalLightGrid(pickedDirectionalLight))
            add(CameraGrid(pickedDirectionalLight))
        }
    }

    fun selectCamera(pickedCamera: Camera) {
        selection = pickedCamera
        sidePanel.doWithRefresh {
            addUnselectButton()
            add(CameraGrid(pickedCamera))
        }
    }
}