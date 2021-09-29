package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.editor.grids.CameraGrid
import de.hanno.hpengine.editor.grids.DirectionalLightGrid
import de.hanno.hpengine.editor.grids.EntityGrid
import de.hanno.hpengine.editor.grids.GiVolumeGrid
import de.hanno.hpengine.editor.grids.MaterialGrid
import de.hanno.hpengine.editor.grids.MeshGrid
import de.hanno.hpengine.editor.grids.ModelGrid
import de.hanno.hpengine.editor.grids.OceanWaterGrid
import de.hanno.hpengine.editor.grids.PointLightGrid
import de.hanno.hpengine.editor.grids.ReflectionProbeGrid
import de.hanno.hpengine.editor.grids.SceneGrid
import de.hanno.hpengine.editor.input.AxisConstraint
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.SelectionMode
import de.hanno.hpengine.editor.scene.SceneTree
import de.hanno.hpengine.editor.scene.SelectionListener
import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.editor.window.setWithRefresh
import de.hanno.hpengine.editor.window.verticalBox
import de.hanno.hpengine.editor.window.verticalBoxOf
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.extension.IdTexture
import de.hanno.hpengine.engine.graphics.CustomGlCanvas
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.SceneManager
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.koin.core.component.get
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL45
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

fun createUnselectButton(): JButton = JButton("Unselect").apply {
    addActionListener {
        removeAll()
    }
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
    val editorInputConfig: EditorInputConfig,
    val gpuContext: GpuContext<OpenGl>,
    val mouseAdapter: MouseAdapterImpl,
    val editor: RibbonEditor,
    val sidePanel: JPanel,
    val renderStateManager: RenderStateManager,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
    val idTexture: IdTexture,
    val sceneManager: SceneManager,
    val sceneTree: SceneTree
) : RenderSystem {

    private val lineVertices = PersistentMappedStructBuffer(100, gpuContext, { HpVector4f() })

    val selectionListener = SelectionListener(sceneTree, this)

    var axisDragged: AxisConstraint = AxisConstraint.None

    val floatBuffer = BufferUtils.createFloatBuffer(4)

    var selection: Selection = Selection.None

    override fun render(result: DrawResult, renderState: RenderState) {
        val currentSelection = selection
        if (axisDragged != AxisConstraint.None) return

        mouseAdapter.mouseClicked?.let { event ->
            floatBuffer.rewind()
            val ratio = Vector2f(
                editor.canvas.width.toFloat() / config.width.toFloat(),
                editor.canvas.height.toFloat() / config.height.toFloat()
            )
            val adjustedX = (event.x / ratio.x).toInt()
            val adjustedY = config.height - (event.y / ratio.y).toInt()
            GL45.glGetTextureSubImage(
                idTexture.texture.id,
                0,
                adjustedX,
                adjustedY,
                0,
                1,
                1,
                1,
                GL11.GL_RGBA,
                GL11.GL_FLOAT,
                floatBuffer
            )
            val entityIndex = floatBuffer.get().toInt()

            onClick(entityIndex)
        }

        when (currentSelection) {
            is SceneSelection -> {
                gpuContext.disable(GlCap.DEPTH_TEST)
                val linePoints = mutableListOf<Vector3fc>().apply {
                    addAABBLines(
                        currentSelection.scene.aabb.min,
                        currentSelection.scene.aabb.max
                    )
                }
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

    private fun onClick(
        entityIndexAsInt: Int
    ) {
        if (entityIndexAsInt >= sceneManager.scene.getEntities().size) {
            println("You picked entity index with in value $entityIndexAsInt as, can't select that")
            return
        }

        val pickedEntity = sceneManager.scene.getEntities()[entityIndexAsInt]
        val meshIndex = floatBuffer.get(3).toInt()
        val modelComponent = pickedEntity.getComponent(ModelComponent::class.java)!!

        selectOrUnselect(MeshSelection(pickedEntity, modelComponent.meshes[meshIndex]))
    }

    val unselectButton = SwingUtils.invokeAndWait {
        JButton("Unselect").apply {
            addActionListener {
                unselect()
            }
        }
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


    fun selectOrUnselect(nextSelection: Selection) {
        if (selection == nextSelection) {
            unselect()
        } else {
            selection = nextSelection
            sceneTree.select(nextSelection)

            when (nextSelection) {
                is EntitySelection -> when (nextSelection) {
                    is CameraSelection -> sidePanelBox { CameraGrid(nextSelection.camera, sceneManager.scene) }
                    is DirectionalLightSelection -> sidePanel.verticalBox(
                        unselectButton,
                        DirectionalLightGrid(nextSelection.light),
                        CameraGrid(nextSelection.light, sceneManager.scene)
                    )
                    is MeshSelection -> {
                        when (editorInputConfig.selectionMode) {
                            SelectionMode.Entity -> {
                                sidePanelBox { EntityGrid(nextSelection.entity) }
                                sceneTree.selectParent(nextSelection)
                            }
                            SelectionMode.Mesh -> {
                                sidePanelBox { MeshGrid(nextSelection.mesh, nextSelection.entity, sceneManager.scene.get())  }
                            }
                        }
                    }
                    is ModelComponentSelection -> sidePanelBox {
                        ModelGrid(
                            nextSelection.modelComponent.model,
                            nextSelection.modelComponent,
                            sceneManager.scene.get()
                        )
                    }
                    is ModelSelection -> sidePanelBox {
                        ModelGrid(
                            nextSelection.modelComponent.model,
                            nextSelection.modelComponent,
                            sceneManager.scene.get()
                        )
                    }
                    is PointLightSelection -> sidePanelBox { PointLightGrid(nextSelection.light) }
                    is SimpleEntitySelection -> sidePanelBox { EntityGrid(nextSelection.entity) }
                }
                is GiVolumeSelection -> sidePanelBox {
                    GiVolumeGrid(
                        gpuContext,
                        config,
                        textureManager,
                        nextSelection.giVolumeComponent,
                        sceneManager
                    )
                }
                is MaterialSelection -> sidePanelBox {
                    MaterialGrid(
                        programManager,
                        textureManager,
                        nextSelection.material
                    )
                }
                is OceanWaterSelection -> sidePanelBox { OceanWaterGrid(nextSelection.oceanWater) }
                is ReflectionProbeSelection -> sidePanelBox { ReflectionProbeGrid(nextSelection.reflectionProbe) }
                is SceneSelection -> sidePanelBox { SceneGrid(nextSelection.scene) }
                Selection.None -> { }
            }.let {}
        }
    }

    private fun sidePanelBox(componentFactory: () -> JComponent) = sidePanel.setWithRefresh {
        verticalBoxOf(componentFactory())
    }
}

