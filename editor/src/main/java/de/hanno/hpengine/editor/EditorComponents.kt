package de.hanno.hpengine.editor

import de.hanno.hpengine.editor.appmenu.ApplicationMenu
import de.hanno.hpengine.editor.input.AxisConstraint
import de.hanno.hpengine.editor.input.EditorInputConfig
import de.hanno.hpengine.editor.input.EditorInputConfigImpl
import de.hanno.hpengine.editor.input.KeyLogger
import de.hanno.hpengine.editor.input.MouseInputProcessor
import de.hanno.hpengine.editor.input.TransformMode
import de.hanno.hpengine.editor.input.TransformSpace
import de.hanno.hpengine.editor.selection.EntitySelection
import de.hanno.hpengine.editor.selection.MouseAdapterImpl
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.supportframes.ConfigFrame
import de.hanno.hpengine.editor.supportframes.TimingsFrame
import de.hanno.hpengine.editor.tasks.MaterialTask
import de.hanno.hpengine.editor.tasks.SceneTask
import de.hanno.hpengine.editor.tasks.TextureTask
import de.hanno.hpengine.editor.tasks.TransformTask
import de.hanno.hpengine.editor.tasks.ViewTask
import de.hanno.hpengine.engine.EngineImpl
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.engine.graphics.renderer.LineRendererImpl
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.renderer.batchAABBLines
import de.hanno.hpengine.engine.graphics.renderer.constants.GlCap
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.VoxelConeTracingExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.AmbientCubeGridExtension
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import org.joml.AxisAngle4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.neon.api.icon.ResizableIcon
import org.pushingpixels.photon.api.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.io.File
import java.util.function.Consumer
import javax.swing.BorderFactory

sealed class OutputConfig {
    object Default : OutputConfig() {
        override fun toString(): String = "Default"
    }
    class Texture2D(val name: String, val texture: de.hanno.hpengine.engine.model.texture.Texture2D) : OutputConfig() {
        override fun toString() = name
    }
    class TextureCubeMap(val name: String, val texture: de.hanno.hpengine.engine.model.texture.CubeMap) : OutputConfig(){
        override fun toString() = name
    }
    data class RenderTargetCubeMapArray(val renderTarget: CubeMapArrayRenderTarget, val cubeMapIndex: Int) : OutputConfig(){
        init {
            val cubeMapArraySize = renderTarget.arraySize
            require(cubeMapIndex < cubeMapArraySize) { "CubeMap index $cubeMapIndex is ot of bounds. Should be smaller than $cubeMapArraySize" }
        }
        override fun toString() = renderTarget.name + cubeMapIndex.toString()
    }
}

class EditorComponents(val engine: EngineImpl,
                       val config: ConfigImpl,
                       val editor: RibbonEditor) : RenderSystem, EditorInputConfig by EditorInputConfigImpl() {

    private var outPutConfig: OutputConfig = OutputConfig.Default
    private val ribbon = editor.ribbon
    private val sidePanel = editor.sidePanel
    private val lineRenderer = LineRendererImpl(engine)
    val sphereHolder = SphereHolder(engine)
    val boxRenderer = SimpleModelRenderer(engine)
    val pyramidRenderer = SimpleModelRenderer(engine, model = StaticModelLoader().load(File("assets/models/pyramid.obj"), engine.materialManager, engine.config.directories.engineDir))
    val torusRenderer = SimpleModelRenderer(engine, model = StaticModelLoader().load(File("assets/models/torus.obj"), engine.materialManager, engine.config.directories.engineDir))
    val environmentProbeSphereHolder = SphereHolder(engine, engine.programManager.getProgramFromFileNames("mvp_vertex.glsl", "environmentprobe_color_fragment.glsl", Defines(Define.getDefine("PROGRAMMABLE_VERTEX_PULLING", true))))

    val sceneTree = SwingUtils.invokeAndWait {
        SceneTree(engine, this).apply {
            addDefaultMouseListener()
            SwingUtils.invokeLater {
                ReloadableScrollPane(this).apply {
                    preferredSize = Dimension(300, editor.sidePanel.height)
                    border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color.BLACK)
                    editor.add(this, BorderLayout.LINE_START)
                }
            }
        }
    }

    val mouseAdapter = MouseAdapterImpl(editor.canvas)
    val selectionSystem = SelectionSystem(this)
    val textureRenderer = SimpleTextureRenderer(engine, engine.deferredRenderingBuffer.colorReflectivenessTexture)

    override fun render(result: DrawResult, state: RenderState) {

        selectionSystem.render(result, state)
        sphereHolder.render(state, draw = { state: RenderState ->
            state.lightState.pointLights.forEach {
                if(it.renderedSphereRadius > 0f) {
                    val transformationPointLight = Transform().scaleAround(it.renderedSphereRadius, it.entity.transform.position.x, it.entity.transform.position.y, it.entity.transform.position.z).translate(it.entity.transform.position)
                    sphereProgram.setUniformAsMatrix4("modelMatrix", transformationPointLight.get(transformBuffer))
                    sphereProgram.setUniform("diffuseColor", Vector3f(it.color.x, it.color.y, it.color.z))

                    draw(sphereVertexIndexBuffer.vertexBuffer,
                            sphereVertexIndexBuffer.indexBuffer,
                            sphereRenderBatch, sphereProgram, false, false)
                }
            }
        })
        drawTransformationArrows(state)

        engine.gpuContext.readBuffer(0)
        selectionSystem.floatBuffer.rewind()
        if(mouseAdapter.mousePressStarted) {
            mouseAdapter.mousePressed?.let { event ->
                run {
                    engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
                    val ratio = Vector2f(editor.canvas.width.toFloat() / engine.config.width.toFloat(),
                            editor.canvas.height.toFloat() / engine.config.height.toFloat())
                    val adjustedX = (event.x / ratio.x).toInt()
                    val adjustedY = engine.config.height - (event.y / ratio.y).toInt()
                    GL11.glReadPixels(adjustedX, adjustedY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, selectionSystem.floatBuffer)

                    val color = Vector3f(selectionSystem.floatBuffer.get(),selectionSystem.floatBuffer.get(),selectionSystem.floatBuffer.get())
                    val axis = if(color.x > 0.9f && color.y < 0.01f && color.z < 0.01f) {
                        AxisConstraint.X
                    } else if(color.y > 0.9f && color.x < 0.01f && color.z < 0.01f) {
                        AxisConstraint.Z
                    } else if (color.z > 0.9f && color.x < 0.01f && color.y < 0.01f) {
                        AxisConstraint.Y
                    } else AxisConstraint.None
                    selectionSystem.axisDragged = axis
                }
            }
        }

        if(config.debug.isEditorOverlay) {
            engine.managerContext.renderSystems.filterIsInstance<ExtensibleDeferredRenderer>().firstOrNull()?.let {
                it.extensions.forEach { it.renderEditor(state, result) }
            }

            for(batch in state.renderBatchesStatic) {
                lineRenderer.batchAABBLines(batch.meshMinWorld, batch.meshMaxWorld)
            }
            for(batch in state.renderBatchesAnimated) {
                lineRenderer.batchAABBLines(batch.meshMinWorld, batch.meshMaxWorld)
            }
            engine.deferredRenderingBuffer.finalBuffer.use(engine.gpuContext, false)
            engine.gpuContext.blend = false
            lineRenderer.drawAllLines(5f, Consumer { program ->
                program.setUniformAsMatrix4("modelMatrix", VoxelConeTracingExtension.identityMatrix44Buffer)
                program.setUniformAsMatrix4("viewMatrix", state.camera.viewMatrixAsBuffer)
                program.setUniformAsMatrix4("projectionMatrix", state.camera.projectionMatrixAsBuffer)
                program.setUniform("diffuseColor", Vector3f(1f, 0f, 0f))
            })
        }
        if(config.debug.visualizeProbes) {
            engine.managerContext.renderSystems.filterIsInstance<ExtensibleDeferredRenderer>().firstOrNull()?.let {
                it.extensions.filterIsInstance<AmbientCubeGridExtension>().firstOrNull()?.let { extension ->
                    engine.gpuContext.depthMask = true
                    engine.gpuContext.disable(GlCap.BLEND)
//                    engine.gpuContext.enable(GlCap.DEPTH_TEST)
                    environmentProbeSphereHolder.render(state) {

                        extension.probeRenderer.probePositions.withIndex().forEach { (probeIndex, position) ->
                            val transformation = Transform().translate(position)
                            sphereProgram.setUniform("pointLightPositionWorld", extension.probeRenderer.probePositions[probeIndex])
                            sphereProgram.setUniform("probeIndex", probeIndex)
                            sphereProgram.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            sphereProgram.setUniform("probeDimensions", extension.probeRenderer.probeDimensions)
                            sphereProgram.bindShaderStorageBuffer(4, extension.probeRenderer.probePositionsStructBuffer)
                            sphereProgram.bindShaderStorageBuffer(5, extension.probeRenderer.probeAmbientCubeValues)

                            draw(sphereVertexIndexBuffer.vertexBuffer,
                                    sphereVertexIndexBuffer.indexBuffer,
                                    sphereRenderBatch, sphereProgram, false, false)
                        }
                    }
                }
            }
        }
        when (val selection = outPutConfig) {
            OutputConfig.Default -> {
                textureRenderer.drawToQuad(engine.deferredRenderingBuffer.finalBuffer, engine.deferredRenderingBuffer.finalMap)
            }
            is OutputConfig.Texture2D -> {
                textureRenderer.drawToQuad(engine.deferredRenderingBuffer.finalBuffer, selection.texture)
            }
            is OutputConfig.TextureCubeMap -> {
                textureRenderer.renderCubeMapDebug(engine.deferredRenderingBuffer.finalBuffer, selection.texture)
            }
            is OutputConfig.RenderTargetCubeMapArray -> {
                textureRenderer.renderCubeMapDebug(engine.deferredRenderingBuffer.finalBuffer, selection.renderTarget, selection.cubeMapIndex)
            }
        }.let { }

        mouseAdapter.reset()
    }

    private fun drawTransformationArrows(state: RenderState) {
        data class Arrow(val scale: Vector3f, val color: Vector3f)
        val ninetyDegrees = Math.toRadians(90.0).toFloat()

        when(val selection = selectionSystem.selection) {
            is EntitySelection -> {
                val entity = selection.entity
                if(transformMode == TransformMode.Rotate) {
                    torusRenderer.render(state, draw = { state: RenderState ->
                        listOf(Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                                Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                                Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))).forEach { arrow ->
                            val transformation = Transform()
                            transformation.scaleLocal(3f)
                            if(arrow.scale.x > 2f) {
                                transformation.rotateAffine(ninetyDegrees, 0f, 0f, 1f)
                            } else if (arrow.scale.y > 2f) {
                                transformation.rotateAffine(ninetyDegrees, 0f, 1f, 0f)
                            } else {
                                transformation.rotateAffine(ninetyDegrees, 1f, 0f, 0f)
                            }
                            when(transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                                TransformSpace.View -> transformation.rotateAroundLocal(state.camera.entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            draw(modelVertexIndexBuffer.vertexBuffer,
                                    modelVertexIndexBuffer.indexBuffer,
                                    modelRenderBatch, program, false, false)
                        }
                    })
                } else {

                    boxRenderer.render(state, draw = { state: RenderState ->
                        listOf(Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                                Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                                Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))).forEach { arrow ->
                            val transformation = Transform()
                            transformation.scaleLocal(arrow.scale.x, arrow.scale.y, arrow.scale.z)
                            transformation.translateLocal(Vector3f(arrow.scale).mul(0.5f).add(entity.transform.position))
                            when(transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                                TransformSpace.View -> transformation.rotateAroundLocal(state.camera.entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            draw(modelVertexIndexBuffer.vertexBuffer,
                                    modelVertexIndexBuffer.indexBuffer,
                                    modelRenderBatch, program, false, false)
                        }
                    })
                    val rotations = listOf(AxisAngle4f(ninetyDegrees, 1f, 0f, 0f), AxisAngle4f(ninetyDegrees, 0f, 1f, 0f), AxisAngle4f(ninetyDegrees, 0f, 0f, -1f))
                    val renderer = if(transformMode == TransformMode.Translate) pyramidRenderer else boxRenderer
                    renderer.render(state, draw = { state: RenderState ->
                        listOf(Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                                Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                                Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))).forEachIndexed { index, arrow ->

                            val transformation = Transform()
                            transformation.rotate(rotations[index]).translateLocal(Vector3f(arrow.scale).add(entity.transform.position))
                            when(transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                                TransformSpace.View -> transformation.rotateAroundLocal(state.camera.entity.transform.rotation, entity.transform.position.x, entity.transform.position.y, entity.transform.position.z)
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            draw(modelVertexIndexBuffer.vertexBuffer,
                                    modelVertexIndexBuffer.indexBuffer,
                                    modelRenderBatch, program, false, false)
                        }
                    })
                }
            }
        }
    }

    init {
        engine.renderSystems.add(this)
        SwingUtils.invokeLater {
            TimingsFrame(engine)
            ribbon.setApplicationMenuCommand(ApplicationMenu(engine))

            addTask(ViewTask(engine, config, this, ::outPutConfig))
            addTask(SceneTask(engine))
            addTask(TransformTask(this, selectionSystem))
            addTask(TextureTask(engine, editor))
            addTask(MaterialTask(engine, editor, selectionSystem))
            showConfigFrame()
        }

        MouseInputProcessor(engine, selectionSystem::selection, this).apply {
            editor.canvas.addMouseMotionListener(this)
            editor.canvas.addMouseListener(this)
        }

        engine.managers.register(EditorManager(this))

        engine.renderSystems.add(this)
    }

    val keyLogger = KeyLogger().apply {
        editor.addKeyListener(this)
    }

    fun isKeyPressed(key: Int) = keyLogger.pressedKeys.contains(key)

    private fun showConfigFrame() = SwingUtils.invokeLater {
        ConfigFrame(engine, config, editor)
    }

    fun addTask(task: RibbonTask) = ribbon.addTask(task)

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