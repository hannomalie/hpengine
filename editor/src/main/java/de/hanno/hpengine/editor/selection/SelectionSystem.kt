package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.grids.CameraGrid
import de.hanno.hpengine.editor.grids.DirectionalLightGrid
import de.hanno.hpengine.editor.grids.EntityGrid
import de.hanno.hpengine.editor.grids.GiVolumeGrid
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.grids.MeshGrid
import de.hanno.hpengine.editor.grids.ModelGrid
import de.hanno.hpengine.editor.grids.PointLightGrid
import de.hanno.hpengine.editor.grids.SceneGrid
import de.hanno.hpengine.editor.input.AxisConstraint
import de.hanno.hpengine.editor.input.SelectionMode
import de.hanno.hpengine.editor.verticalBox
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.batchAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.transform.Transform
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Consumer
import javax.swing.JButton
import javax.swing.JPanel

fun JPanel.addUnselectButton(additionalOnClick: () -> Unit = {}) {
    add(JButton("Unselect").apply {
        addActionListener {
            removeAll()
            additionalOnClick()
        }
    })
}

class MouseAdapterImpl(canvas: CustomGlCanvas): MouseAdapter() {
    var mouseClicked: MouseEvent? = null
        private set
    var mousePressed: MouseEvent? = null
        private set
    var mousePressStarted = false
        private set
    init {
        canvas.addMouseListener(this)
    }
    override fun mouseClicked(e: MouseEvent) {
        mouseClicked = e
    }

    override fun mousePressed(e: MouseEvent) {
        if(mousePressed == null) mousePressStarted = true
        mousePressed = e
    }

    override fun mouseReleased(e: MouseEvent) {
        mousePressed = null
        mousePressStarted = false
    }
    fun reset() {
        mouseClicked = null
        mousePressStarted = false
    }
}

class SelectionSystem(val editorComponents: EditorComponents) : RenderSystem {
    val mouseAdapter = editorComponents.mouseAdapter
    val engine: Engine<OpenGl> = editorComponents.engine
    val editor: RibbonEditor = editorComponents.editor
    val sidePanel = editorComponents.editor.sidePanel
    val lineRenderer = LineRendererImpl(engine)

    val simpleColorProgramStatic = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true)))
    val simpleColorProgramAnimated = engine.programManager.getProgramFromFileNames("first_pass_vertex.glsl", "first_pass_fragment.glsl", Defines(Define.getDefine("COLOR_OUTPUT_0", true), Define.getDefine("ANIMATED", true)))

    var axisDragged: AxisConstraint = AxisConstraint.None

    val floatBuffer = BufferUtils.createFloatBuffer(4)

    var selection: Selection = Selection.None

    private val identityMatrix44Buffer = BufferUtils.createFloatBuffer(16).apply {
        Transform().get(this)
    }

    override fun render(result: DrawResult, state: RenderState) {
        val selection = selection
        if(axisDragged != AxisConstraint.None) return

        mouseAdapter.mouseClicked?.let { event ->
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

            when(selection) {
                is EntitySelection -> {
                    when(selection) {
                        is ModelSelection -> if(selection.model.path == modelComponent!!.model.path) unselect() else selectModel(selection)
                        is MeshSelection -> {
                            val selectedMesh = modelComponent?.meshes?.get(meshIndex.toInt())
                            when {
                                selection.mesh.name == selectedMesh?.name -> {
                                    unselect()
                                }
                                selectedMesh != null -> {
                                    val pickedMesh = MeshSelection(pickedEntity, selectedMesh)
                                    selectMesh(pickedMesh)
                                    editorComponents.sceneTree.select(selectedMesh)
                                }
                                else -> Unit
                            }
                        }
                        else -> if(selection.entity.name == pickedEntity.name) unselect() else {
                            selectEntity(pickedEntity)
                            editorComponents.sceneTree.select(pickedEntity)
                        }
                    }
                }
                Selection.None -> when(editorComponents.selectionMode) {
                    SelectionMode.Entity -> {
                        selectEntity(pickedEntity)
                        editorComponents.sceneTree.select(pickedEntity)
                    }
                    SelectionMode.Mesh -> {
                        if(modelComponent != null) {
                            val selectedMesh = modelComponent.meshes[meshIndex.toInt()]
                            val pickedMesh = MeshSelection(pickedEntity, selectedMesh)
                            selectMesh(pickedMesh)
                            editorComponents.sceneTree.select(selectedMesh)
                        } else Unit
                    }
                }
                is Material -> selectMaterial(selection)
                else -> Unit
            }.let {}
        }

        when(selection) {
            is SceneSelection -> {
                engine.gpuContext.disable(GlCap.DEPTH_TEST)
                engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
                lineRenderer.batchAABBLines(selection.scene.minMax.min, selection.scene.minMax.max)
                lineRenderer.drawAllLines(5f, Consumer { program ->
                    program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
                    program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                    program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                    program.setUniform("diffuseColor", Vector3f(1f, 0f, 1f))
                })
            }
            is EntitySelection -> {
//                val entity = selection.entity
//                val mesh = if (selection is MeshSelection) selection.mesh else null
//
//                engine.gpuContext.disable(GlCap.DEPTH_TEST)
//                engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
//                val origin = if (mesh != null) Vector3f(mesh.minMax.min).add(Vector3f(mesh.minMax.max).sub(mesh.minMax.min).mul(0.5f)) else entity.position
//                val arrowLength = state.camera.getPosition().distance(origin).coerceIn(0.5f, 20f)
//                lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.rightDirection).mul(arrowLength)))
//                lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.upDirection).mul(arrowLength)))
//                lineRenderer.batchLine(origin, Vector3f(origin).add(Vector3f(entity.viewDirection).mul(arrowLength)))
//                lineRenderer.drawAllLines(5f, Consumer { program ->
//                    program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
//                    program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
//                    program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
//                    program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
//                })
//                val constraintAxisVectorWorld = Vector3f(editorComponents.constraintAxis.axis)
//                val constraintAxisVector = when (editorComponents.transformSpace) {
//                    TransformSpace.World -> constraintAxisVectorWorld
//                    TransformSpace.Local -> constraintAxisVectorWorld.rotate(entity.rotation)
//                    TransformSpace.View -> constraintAxisVectorWorld
//                }
//                lineRenderer.batchLine(origin, Vector3f(origin).add(constraintAxisVector.mul(arrowLength)))
//                lineRenderer.drawAllLines(5f, Consumer { program ->
//                    program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
//                    program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
//                    program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
//                    program.setUniform("diffuseColor", Vector3f(1f, 0f, 1f))
//                })
//
//                if (mesh != null) {
//                    lineRenderer.batchAABBLines(mesh.minMax.min, mesh.minMax.max)
//                } else {
//                    lineRenderer.batchAABBLines(entity.minMax.min, entity.minMax.max)
//                }
//                lineRenderer.drawAllLines(5f, Consumer { program ->
//                    program.setUniformAsMatrix4("modelMatrix", identityMatrix44Buffer)
//                    program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
//                    program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
//                    program.setUniform("diffuseColor", Vector3f(0f, 0f, 1f))
//                })
//
//                state.renderBatchesStatic.filter { batch ->
//                    val modelComponent = entity.getComponent(ModelComponent::class.java)
//                    val meshFilter = mesh?.let {
//                        val meshIndex = modelComponent?.meshes?.indexOf(it) ?: -100
//                        batch.meshIndex == meshIndex
//                    } ?: true
//
//                    batch.entityIndex == entity.index && meshFilter
//                }.forEach {
//                    engine.gpuContext.enable(GlCap.BLEND)
//                    engine.gpuContext.blendEquation = BlendMode.FUNC_ADD
//                    engine.gpuContext.blendFunc(BlendMode.Factor.ONE, BlendMode.Factor.ONE)
////                TODO: Enable as soon as builder stuff for rendertargets is fixed
////                engine.gpuContext.enable(GlCap.DEPTH_TEST)
////                engine.gpuContext.depthFunc(GlDepthFunc.EQUAL)
//                    simpleColorProgramStatic.use()
//                    simpleColorProgramStatic.bindShaderStorageBuffer(1, state.entitiesState.materialBuffer)
//                    simpleColorProgramStatic.bindShaderStorageBuffer(3, state.entitiesState.entitiesBuffer)
//                    simpleColorProgramStatic.bindShaderStorageBuffer(6, state.entitiesState.jointsBuffer)
//                    simpleColorProgramStatic.bindShaderStorageBuffer(7, state.entitiesState.vertexIndexBufferStatic.vertexStructArray)
//                    simpleColorProgramStatic.setTextureUniforms(engine.gpuContext, it.materialInfo.maps)
//                    simpleColorProgramStatic.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
//                    simpleColorProgramStatic.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
//                    simpleColorProgramStatic.setUniformAsMatrix4("viewProjectionMatrix", state.camera.viewProjectionMatrixAsBuffer)
//                    draw(state.vertexIndexBufferStatic.vertexBuffer,
//                            state.vertexIndexBufferStatic.indexBuffer, it, simpleColorProgramStatic, false, false)
//                }
            }
        }
    }

    val unselectButton = SwingUtils.invokeAndWait {
        JButton("Unselect").apply {
            addActionListener {
                unselect()
            }
        }
    }
    fun selectPointLight(pickedPointLight: PointLight) = SwingUtils.invokeLater {
        selection = PointLightSelection(pickedPointLight)
        sidePanel.verticalBox(
            unselectButton,
            PointLightGrid(pickedPointLight)
        )
    }

    fun selectEntity(pickedEntity: Entity) = SwingUtils.invokeLater {
        selection = EntitySelection(pickedEntity)
        sidePanel.verticalBox(
            unselectButton,
            EntityGrid(pickedEntity)
        )
    }

    fun selectModel(pickedModel: ModelSelection) = SwingUtils.invokeLater {
        selection = pickedModel
        sidePanel.verticalBox(
            unselectButton,
            ModelGrid(pickedModel.model, pickedModel.modelComponent, engine.scene.materialManager)
        )
    }

    fun selectMesh(pickedMesh: MeshSelection) = SwingUtils.invokeLater {
        selection = pickedMesh
        sidePanel.verticalBox(
            unselectButton,
            MeshGrid(pickedMesh.mesh, pickedMesh.entity, engine.scene.materialManager)
        )
    }

    fun selectMaterial(pickedMaterial: Material) = SwingUtils.invokeLater {
        selection = MaterialSelection(pickedMaterial)
        sidePanel.verticalBox(
            unselectButton,
            MaterialGrid(engine.textureManager, pickedMaterial)
        )
    }

    fun selectGiVolume(giVolumeComponent: GIVolumeComponent) = SwingUtils.invokeLater {
        selection = GiVolumeSelection(giVolumeComponent)
        sidePanel.verticalBox(
            unselectButton,
            GiVolumeGrid(giVolumeComponent, engine)
        )
    }

    fun unselect() {
        SwingUtils.invokeLater {
            selection = Selection.None
            sidePanel.removeAll()
            sidePanel.revalidate()
            sidePanel.add(editor.emptySidePanel)
            sidePanel.repaint()
        }
    }

    fun selectDirectionalLight(pickedDirectionalLight: DirectionalLight) = SwingUtils.invokeLater {
        selection = DirectionalLightSelection(pickedDirectionalLight)
        sidePanel.verticalBox(
            unselectButton,
            DirectionalLightGrid(pickedDirectionalLight),
            CameraGrid(pickedDirectionalLight)
        )
    }

    fun selectCamera(pickedCamera: Camera) = SwingUtils.invokeLater {
        selection = CameraSelection(pickedCamera)
        sidePanel.verticalBox(
            unselectButton,
            CameraGrid(pickedCamera)
        )
    }

    fun selectScene(pickedScene: Scene) = SwingUtils.invokeLater {
        selection = SceneSelection(pickedScene)
        sidePanel.verticalBox(
            unselectButton,
            SceneGrid(pickedScene)
        )
    }
}