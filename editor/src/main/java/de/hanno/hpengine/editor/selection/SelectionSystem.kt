package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.editor.grids.CameraGrid
import de.hanno.hpengine.editor.grids.DirectionalLightGrid
import de.hanno.hpengine.editor.grids.EntityGrid
import de.hanno.hpengine.editor.grids.GiVolumeGrid
import de.hanno.hpengine.editor.grids.OceanWaterGrid
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.grids.MeshGrid
import de.hanno.hpengine.editor.grids.ModelGrid
import de.hanno.hpengine.editor.grids.PointLightGrid
import de.hanno.hpengine.editor.grids.ReflectionProbeGrid
import de.hanno.hpengine.editor.grids.SceneGrid
import de.hanno.hpengine.editor.input.AxisConstraint
import de.hanno.hpengine.editor.input.SelectionMode
import de.hanno.hpengine.editor.verticalBox
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.material.Material
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.OceanWaterExtension
import de.hanno.hpengine.engine.scene.Scene
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.koin.core.component.get
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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

class SelectionSystem(
    val config: Config,
    val gpuContext: GpuContext<OpenGl>,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val editorComponents: EditorComponents,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager
) : RenderSystem {
    val mouseAdapter = editorComponents.mouseAdapter
    val editor = editorComponents.editor
    val sidePanel = editorComponents.editor.sidePanel
    private val lineVertices = PersistentMappedStructBuffer(100, gpuContext, { HpVector4f() })

    var axisDragged: AxisConstraint = AxisConstraint.None

    val floatBuffer = BufferUtils.createFloatBuffer(4)

    var selection: Selection = Selection.None

    override fun render(result: DrawResult, renderState: RenderState) {
        val selection = selection
        if(axisDragged != AxisConstraint.None) return

        mouseAdapter.mouseClicked?.let { event ->
            deferredRenderingBuffer.use(gpuContext, false)
            gpuContext.readBuffer(4)
            floatBuffer.rewind()
            val ratio = Vector2f(editor.canvas.width.toFloat() / config.width.toFloat(),
                    editor.canvas.height.toFloat() / config.height.toFloat())
            val adjustedX = (event.x / ratio.x).toInt()
            val adjustedY = config.height - (event.y / ratio.y).toInt()
            GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floatBuffer)

            val entityIndex = floatBuffer.get()
            val entityIndexAsInt = entityIndex.toInt()
            if(entityIndexAsInt in editorComponents.sceneManager.scene.getEntities().indices) {
                val pickedEntity = editorComponents.sceneManager.scene.getEntities()[entityIndexAsInt]
                val meshIndex = floatBuffer.get(3)
                val modelComponent = pickedEntity.getComponent(ModelComponent::class.java)

//             TODO: This is too ugly, recode
                when(selection) {
                    is EntitySelection -> {
                        when(selection) {
                            is ModelSelection -> if(selection.model.path == modelComponent?.model?.path) unselect() else selectModel(selection)
                            is MeshSelection -> {
                                val selectedMesh = modelComponent?.meshes?.get(meshIndex.toInt())
                                when {
                                    selection.mesh == selectedMesh -> {
                                        unselect()
                                        editorComponents.sceneTree.unselect()
                                    }
                                    selectedMesh != null -> {
                                        val pickedMesh = MeshSelection(pickedEntity, selectedMesh)
                                        editorComponents.sceneTree.select(MeshSelection(modelComponent.entity, selectedMesh))
                                        selectMesh(pickedMesh)
                                    }
                                    else -> Unit
                                }
                            }
                            else -> {
                                when(editorComponents.selectionMode) {
                                    SelectionMode.Entity -> {
                                        if(selection.entity.name == pickedEntity.name) {
                                            unselect()
                                            editorComponents.sceneTree.unselect()
                                        } else {
                                            selectEntity(pickedEntity)
                                            editorComponents.sceneTree.select(pickedEntity)
                                        }
                                    }
                                    SelectionMode.Mesh -> {
                                        val selectedMesh = modelComponent?.meshes?.get(meshIndex.toInt())
                                        if(selectedMesh != null) {
                                            val pickedMesh = MeshSelection(pickedEntity, selectedMesh)
                                            editorComponents.sceneTree.select(MeshSelection(modelComponent.entity, selectedMesh))
                                            selectMesh(pickedMesh)
                                        } else Unit
                                    }
                                }
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
                                editorComponents.sceneTree.select(MeshSelection(modelComponent.entity, selectedMesh))
                                selectMesh(pickedMesh)
                            } else Unit
                        }
                    }
                    is Material -> selectMaterial(selection)
                    else -> Unit
                }.let {}
            } else {
                println("You picked $entityIndex which is a $entityIndexAsInt as Int, can't select that")
            }
        }

        when(selection) {
            is SceneSelection -> {
                gpuContext.disable(GlCap.DEPTH_TEST)
                deferredRenderingBuffer.finalBuffer.use(gpuContext, false)
                val linePoints = mutableListOf<Vector3fc>().apply { addAABBLines(selection.scene.aabb.min, selection.scene.aabb.max) }
                drawLines(renderStateManager, programManager, lineVertices, linePoints, color = Vector3f(1f, 0f, 1f))
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
            ModelGrid(pickedModel.model, pickedModel.modelComponent, editorComponents.sceneManager.scene.get())
        )
    }

    fun selectMesh(pickedMesh: MeshSelection) = SwingUtils.invokeLater {
        selection = pickedMesh
        sidePanel.verticalBox(
            unselectButton,
            MeshGrid(pickedMesh.mesh, pickedMesh.entity, editorComponents.sceneManager.scene.get())
        )
    }
    fun selectReflectionProbe(pickedReflectionProbe: ReflectionProbeSelection) = SwingUtils.invokeLater {
        selection = pickedReflectionProbe
        sidePanel.verticalBox(
            unselectButton,
            ReflectionProbeGrid(pickedReflectionProbe.reflectionProbe)
        )
    }

    fun selectMaterial(pickedMaterial: Material) = SwingUtils.invokeLater {
        selection = MaterialSelection(pickedMaterial)
        sidePanel.verticalBox(
            unselectButton,
            MaterialGrid(programManager, textureManager, pickedMaterial)
        )
    }

    fun selectGiVolume(giVolumeComponent: GIVolumeComponent) = SwingUtils.invokeLater {
        selection = GiVolumeSelection(giVolumeComponent)
        sidePanel.verticalBox(
                unselectButton,
                GiVolumeGrid(gpuContext, config, textureManager, giVolumeComponent, editorComponents.sceneManager)
        )
    }
    fun selectOceanWater(oceanWaterComponent: OceanWaterExtension.OceanWater) = SwingUtils.invokeLater {
        selection = OceanWaterSelection(oceanWaterComponent)
        sidePanel.verticalBox(
                unselectButton,
                OceanWaterGrid(oceanWaterComponent)
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

    fun selectDirectionalLight(pickedDirectionalLight: DirectionalLight, scene: Scene) = SwingUtils.invokeLater {
        selection = DirectionalLightSelection(pickedDirectionalLight)
        sidePanel.verticalBox(
            unselectButton,
            DirectionalLightGrid(pickedDirectionalLight),
            CameraGrid(pickedDirectionalLight, scene)
        )
    }

    fun selectCamera(pickedCamera: Camera, scene: Scene) = SwingUtils.invokeLater {
        selection = CameraSelection(pickedCamera)
        sidePanel.verticalBox(
            unselectButton,
            CameraGrid(pickedCamera, scene)
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