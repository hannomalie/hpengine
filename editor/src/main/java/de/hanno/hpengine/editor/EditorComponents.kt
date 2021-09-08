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
import de.hanno.hpengine.editor.tasks.EditorRibbonTask
import de.hanno.hpengine.editor.tasks.MaterialRibbonTask
import de.hanno.hpengine.editor.tasks.SceneTask
import de.hanno.hpengine.editor.tasks.TextureTask
import de.hanno.hpengine.editor.tasks.TransformTask
import de.hanno.hpengine.editor.tasks.ViewTask
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.ConfigImpl
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.extension.IdTexture
import de.hanno.hpengine.engine.graphics.GpuContext
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.Window
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.renderer.addAABBLines
import de.hanno.hpengine.engine.graphics.renderer.drawLines
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.draw
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IntStruct
import de.hanno.hpengine.engine.graphics.renderer.pipelines.PersistentMappedStructBuffer
import de.hanno.hpengine.engine.graphics.renderer.putLinesPoints
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.CubeMapArrayRenderTarget
import de.hanno.hpengine.engine.graphics.renderer.rendertarget.FrameBuffer
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.instancing.clusters
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.loader.assimp.StaticModelLoader
import de.hanno.hpengine.engine.model.texture.Texture2D
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.HpVector4f
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.engine.scene.SceneManager
import de.hanno.hpengine.engine.transform.Transform
import de.hanno.hpengine.util.gui.container.ReloadableScrollPane
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.joml.AxisAngle4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.Vector4f
import org.lwjgl.opengl.GL11
import org.pushingpixels.flamingo.api.common.icon.ImageWrapperResizableIcon
import org.pushingpixels.flamingo.api.ribbon.JRibbon
import org.pushingpixels.flamingo.api.ribbon.RibbonTask
import org.pushingpixels.neon.api.icon.ResizableIcon
import org.pushingpixels.photon.api.icon.SvgBatikResizableIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import javax.swing.BorderFactory

sealed class OutputConfig {
    object Default : OutputConfig() {
        override fun toString(): String = "Default"
    }

    class Texture2D(
        val name: String,
        val texture: de.hanno.hpengine.engine.model.texture.Texture2D,
        val factorForDebugRendering: Float
    ) : OutputConfig() {
        override fun toString() = name
    }

    class TextureCubeMap(val name: String, val texture: de.hanno.hpengine.engine.model.texture.CubeMap) :
        OutputConfig() {
        override fun toString() = name
    }

    data class RenderTargetCubeMapArray(val renderTarget: CubeMapArrayRenderTarget, val cubeMapIndex: Int) :
        OutputConfig() {
        init {
            val cubeMapArraySize = renderTarget.arraySize
            require(cubeMapIndex < cubeMapArraySize) { "CubeMap index $cubeMapIndex is ot of bounds. Should be smaller than $cubeMapArraySize" }
        }

        override fun toString() = renderTarget.name + cubeMapIndex.toString()
    }
}

val JRibbon.tasks: List<RibbonTask>
    get() = mutableListOf<RibbonTask>().apply {
        addAll((0 until taskCount).map { getTask(it) })
    }

class EditorComponents(
    val gpuContext: GpuContext<OpenGl>,
    val config: ConfigImpl,
    val window: Window<OpenGl>,
    val editor: RibbonEditor,
    val programManager: ProgramManager<OpenGl>,
    val textureManager: TextureManager,
    val addResourceContext: AddResourceContext,
    val renderStateManager: RenderStateManager,
    val sceneManager: SceneManager,
    val targetTexture: Texture2D,
    val idTexture: IdTexture,
    val editorInputConfig: EditorInputConfigImpl,
    val sceneTree: SceneTree
) : RenderSystem, EditorInputConfig by editorInputConfig, Manager {

    val targetBuffer = de.hanno.hpengine.engine.graphics.renderer.rendertarget.RenderTarget(
        gpuContext,
        FrameBuffer(
            gpuContext,
            null
        ),
        targetTexture.dimension.width,
        targetTexture.dimension.height,
        listOf(targetTexture),
        "EditorFinalOutput",
        Vector4f(0f)
    )
    val onReload: (() -> Unit)?
        get() = editor.onSceneReload
    private var outPutConfig: OutputConfig = OutputConfig.Default
    private val ribbon = editor.ribbon
    private val sidePanel = editor.sidePanel
    val sphereHolder = SphereHolder(
        config,
        textureManager,
        gpuContext,
        programManager,
        targetBuffer = targetBuffer
    )
    val boxRenderer = SimpleModelRenderer(
        config,
        textureManager,
        gpuContext,
        programManager,
        targetBuffer = targetBuffer
    )
    val pyramidRenderer = SimpleModelRenderer(
        config, textureManager, gpuContext, programManager, model = StaticModelLoader().load(
            "assets/models/pyramid.obj",
            textureManager,
            config.directories.engineDir
        ), targetBuffer = targetBuffer
    )
    val torusRenderer = SimpleModelRenderer(
        config, textureManager, gpuContext, programManager, model = StaticModelLoader().load(
            "assets/models/torus.obj",
            textureManager,
            config.directories.engineDir
        ), targetBuffer = targetBuffer
    )
    val environmentProbeSphereHolder = SphereHolder(
        config,
        textureManager,
        gpuContext,
        programManager,
        programManager.run {
            getProgram(
                config.EngineAsset("shaders/mvp_vertex.glsl").toCodeSource(),
                config.EngineAsset("shaders/environmentprobe_color_fragment.glsl").toCodeSource()
            )
        },
        targetBuffer
    )

    val mouseAdapter = MouseAdapterImpl(editor.canvas)

    val selectionSystem = SelectionSystem(
        config,
        editorInputConfig,
        gpuContext,
        mouseAdapter,
        editor,
        sidePanel,
        renderStateManager,
        programManager,
        textureManager,
        idTexture,
        sceneManager,
        sceneTree
    )
    val textureRenderer = SimpleTextureRenderer(
        config,
        gpuContext,
        targetBuffer.textures.first(),
        programManager,
        window.frontBuffer
    )

    private var sceneTreePane: ReloadableScrollPane? = null
    val aabbLines = mutableListOf<Vector3fc>()
    val lineVertices = renderStateManager.renderState.registerState {
        PersistentMappedStructBuffer(
            100,
            gpuContext,
            { HpVector4f() })
    }
    val lineVerticesCount = renderStateManager.renderState.registerState { IntStruct() }
    val selectionTransform =
        renderStateManager.renderState.registerState { Transform().apply { identity() } }

    override fun onEntityAdded(entities: List<Entity>) {
        ribbon.editorTasks.forEach { it.reloadContent() }
    }

    override fun onComponentAdded(component: Component) {
        ribbon.editorTasks.forEach { it.reloadContent() }
    }

    override fun afterSetScene(lastScene: Scene?, currentScene: Scene) {
        ribbon.editorTasks.forEach { it.reloadContent() }
    }

    private val JRibbon.editorTasks
        get() = tasks.toList().filterIsInstance<EditorRibbonTask>()

    override fun extract(scene: Scene, renderState: RenderState) {
        aabbLines.apply {
            clear()
            scene.getEntities().mapNotNull { it.getComponent(ModelComponent::class.java) }.forEach { modelComponent ->
                modelComponent.meshes.forEach {
                    val boundingVolume = modelComponent.getBoundingVolume(modelComponent.entity.transform, it)
                    addAABBLines(boundingVolume.min, boundingVolume.max)
                }
                modelComponent.entity.clusters.forEach { cluster ->
                    val clusterVolume = cluster.boundingVolume
                    addAABBLines(clusterVolume.min, clusterVolume.max)
                }
            }
        }
        renderState[lineVertices].putLinesPoints(aabbLines)
        renderState[lineVerticesCount].value = aabbLines.size
        when (val selection = selectionSystem.selection) {
            is EntitySelection -> renderState[selectionTransform].set(selection.entity.transform)
        }
    }

    override fun renderEditor(result: DrawResult, renderState: RenderState) {
        selectionSystem.render(result, renderState)
        sphereHolder.render(renderState, draw = { state: RenderState ->
            state.lightState.pointLights.forEach {
                if (it.renderedSphereRadius > 0f) {
                    val transformationPointLight = Transform().scaleAround(
                        it.renderedSphereRadius,
                        it.entity.transform.position.x,
                        it.entity.transform.position.y,
                        it.entity.transform.position.z
                    ).translate(it.entity.transform.position)
                    sphereProgram.setUniformAsMatrix4("modelMatrix", transformationPointLight.get(transformBuffer))
                    sphereProgram.setUniform("diffuseColor", Vector3f(it.color.x, it.color.y, it.color.z))

                    sphereVertexIndexBuffer.indexBuffer.draw(sphereRenderBatch, sphereProgram, bindIndexBuffer = false)
                }
            }
        })
        drawTransformationArrows(renderState)

        gpuContext.readBuffer(0)
        selectionSystem.floatBuffer.rewind()
        if (mouseAdapter.mousePressStarted) {
            mouseAdapter.mousePressed?.let { event ->
                run {
                    targetBuffer.use(gpuContext, false)
                    val ratio = Vector2f(
                        editor.canvas.width.toFloat() / config.width.toFloat(),
                        editor.canvas.height.toFloat() / config.height.toFloat()
                    )
                    val adjustedX = (event.x / ratio.x).toInt()
                    val adjustedY = config.height - (event.y / ratio.y).toInt()
                    GL11.glReadPixels(
                        adjustedX,
                        adjustedY,
                        1,
                        1,
                        GL11.GL_RGBA,
                        GL11.GL_FLOAT,
                        selectionSystem.floatBuffer
                    )

                    val color = Vector3f(
                        selectionSystem.floatBuffer.get(),
                        selectionSystem.floatBuffer.get(),
                        selectionSystem.floatBuffer.get()
                    )
                    val axis = if (color.x > 0.9f && color.y < 0.01f && color.z < 0.01f) {
                        AxisConstraint.X
                    } else if (color.y > 0.9f && color.x < 0.01f && color.z < 0.01f) {
                        AxisConstraint.Z
                    } else if (color.z > 0.9f && color.x < 0.01f && color.y < 0.01f) {
                        AxisConstraint.Y
                    } else AxisConstraint.None
                    selectionSystem.axisDragged = axis
                }
            }
        }

        if (config.debug.isDrawBoundingVolumes) {
            targetBuffer.use(gpuContext, false)
            gpuContext.blend = false
            gpuContext.depthTest = true

            drawLines(
                renderStateManager = renderStateManager,
                programManager = programManager,
                vertices = renderState[lineVertices],
                verticesCount = renderState[lineVerticesCount].value,
                color = Vector3f(1f, 0f, 0f)
            )
        }

        when (val selection = outPutConfig) {
            OutputConfig.Default -> {
                textureRenderer.drawToQuad(
                    targetBuffer.renderedTexture
                )
            }
            is OutputConfig.Texture2D -> {
                textureRenderer.drawToQuad(
                    selection.texture,
                    program = textureRenderer.debugFrameProgram,
                    factorForDebugRendering = selection.factorForDebugRendering
                )
            }
            is OutputConfig.TextureCubeMap -> {
                textureRenderer.renderCubeMapDebug(targetBuffer, selection.texture)
            }
            is OutputConfig.RenderTargetCubeMapArray -> {
                textureRenderer.renderCubeMapDebug(
                    targetBuffer,
                    selection.renderTarget,
                    selection.cubeMapIndex
                )
            }
        }.let { }

        mouseAdapter.reset()
    }

    private fun drawTransformationArrows(state: RenderState) {
        data class Arrow(val scale: Vector3f, val color: Vector3f)

        val ninetyDegrees = Math.toRadians(90.0).toFloat()

        gpuContext.cullFace = false
        when (selectionSystem.selection) {
            is EntitySelection -> {
                val transform = state[selectionTransform]

                if (transformMode == TransformMode.Rotate) {
                    torusRenderer.render(state, draw = { state: RenderState ->
                        listOf(
                            Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                            Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                            Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))
                        ).forEach { arrow ->
                            val transformation = Transform()
                            transformation.scaleLocal(3f)
                            when {
                                arrow.scale.x > 2f -> transformation.rotateAffine(ninetyDegrees, 0f, 0f, 1f)
                                arrow.scale.y > 2f -> transformation.rotateAffine(ninetyDegrees, 0f, 1f, 0f)
                                else -> transformation.rotateAffine(ninetyDegrees, 1f, 0f, 0f)
                            }
                            when (transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(
                                    transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                                TransformSpace.View -> transformation.rotateAroundLocal(
                                    state.camera.entity.transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            modelVertexIndexBuffer.indexBuffer.draw(modelRenderBatch, program, bindIndexBuffer = false)
                        }
                    })
                } else {

                    boxRenderer.render(state, draw = { state: RenderState ->
                        listOf(
                            Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                            Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                            Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))
                        ).forEach { arrow ->
                            val transformation = Transform()
                            transformation.scaleLocal(arrow.scale.x, arrow.scale.y, arrow.scale.z)
                            transformation.translateLocal(Vector3f(arrow.scale).mul(0.5f).add(transform.position))
                            when (transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(
                                    transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                                TransformSpace.View -> transformation.rotateAroundLocal(
                                    state.camera.entity.transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            modelVertexIndexBuffer.indexBuffer.draw(modelRenderBatch, program, false)
                        }
                    })
                    val rotations = listOf(
                        AxisAngle4f(ninetyDegrees, 1f, 0f, 0f),
                        AxisAngle4f(ninetyDegrees, 0f, 1f, 0f),
                        AxisAngle4f(ninetyDegrees, 0f, 0f, -1f)
                    )
                    val renderer = if (transformMode == TransformMode.Translate) pyramidRenderer else boxRenderer
                    renderer.render(state, draw = { state: RenderState ->
                        listOf(
                            Arrow(Vector3f(0.1f, 0.1f, 5f), Vector3f(0f, 1f, 0f)),
                            Arrow(Vector3f(0.1f, 5f, 0.1f), Vector3f(0f, 0f, 1f)),
                            Arrow(Vector3f(5f, 0.1f, 0.1f), Vector3f(1f, 0f, 0f))
                        ).forEachIndexed { index, arrow ->

                            val transformation = Transform()
                            transformation.rotate(rotations[index])
                                .translateLocal(Vector3f(arrow.scale).add(transform.position))
                            when (transformSpace) {
                                TransformSpace.World -> Unit
                                TransformSpace.Local -> transformation.rotateAroundLocal(
                                    transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                                TransformSpace.View -> transformation.rotateAroundLocal(
                                    state.camera.entity.transform.rotation,
                                    transform.position.x,
                                    transform.position.y,
                                    transform.position.z
                                )
                            }
                            program.setUniformAsMatrix4("modelMatrix", transformation.get(transformBuffer))
                            program.setUniform("diffuseColor", arrow.color)

                            modelVertexIndexBuffer.indexBuffer.draw(modelRenderBatch, program, false)
                        }
                    })
                }
            }
        }
    }

    init {
        MouseInputProcessor(window, addResourceContext, selectionSystem::selection, this).apply {
            editor.canvas.addMouseMotionListener(this)
            editor.canvas.addMouseListener(this)
        }
        SwingUtils.invokeLater {
            ribbon.setApplicationMenuCommand(ApplicationMenu(sceneManager))

            addTask(ViewTask(gpuContext, this, ::outPutConfig, addResourceContext))
            addTask(SceneTask(sceneManager, this))
            addTask(TransformTask(this, selectionSystem))
            addTask(TextureTask(gpuContext, textureManager, editor, selectionSystem))
            addTask(MaterialRibbonTask(addResourceContext, textureManager, programManager, sceneManager, editor, selectionSystem))
        }
        SwingUtils.invokeLater {
            TimingsFrame()
            ConfigFrame(config, editor)
        }
    }

    override fun beforeSetScene(nextScene: Scene) {
        SwingUtils.invokeLater {
            sceneTree.apply {
                addDefaultMouseListener()
                SwingUtils.invokeLater {
                    sceneTreePane = ReloadableScrollPane(this).apply {
                        preferredSize = Dimension(300, editor.sidePanel.height)
                        border = BorderFactory.createMatteBorder(0, 0, 0, 1, Color.BLACK)
                        sceneTreePane?.let { editor.remove(it) }
                        editor.add(this, BorderLayout.LINE_START)
                    }
                }
            }
        }
    }

    val keyLogger = KeyLogger().apply {
        editor.addKeyListener(this)
    }

    fun isKeyPressed(key: Int) = keyLogger.pressedKeys.contains(key)

    fun addTask(task: RibbonTask) = ribbon.addTask(task)

    companion object {

        fun getResizableIconFromSvgResource(resource: String): ResizableIcon {
            return SvgBatikResizableIcon.getSvgIcon(
                RibbonEditor::class.java.classLoader.getResource(resource),
                Dimension(24, 24)
            )
        }

        fun getResizableIconFromImageSource(resource: String): ResizableIcon {
            return ImageWrapperResizableIcon.getIcon(
                RibbonEditor::class.java.classLoader.getResource(resource),
                Dimension(24, 24)
            )
        }

        fun getResizableIconFromImageSource(image: Image): ResizableIcon {
            return ImageWrapperResizableIcon.getIcon(image, Dimension(24, 24))
        }
    }
}