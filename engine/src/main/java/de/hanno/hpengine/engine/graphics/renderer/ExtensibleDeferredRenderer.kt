package de.hanno.hpengine.engine.graphics.renderer

import com.artemis.World
import de.hanno.hpengine.engine.backend.Backend
import de.hanno.hpengine.engine.backend.OpenGl
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.*
import de.hanno.hpengine.engine.graphics.renderer.constants.CullMode
import de.hanno.hpengine.engine.graphics.renderer.constants.GlDepthFunc
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.DrawResult
import de.hanno.hpengine.engine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.engine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.engine.graphics.renderer.pipelines.AnimatedFirstPassUniforms
import de.hanno.hpengine.engine.graphics.renderer.pipelines.DirectPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.IndirectPipeline
import de.hanno.hpengine.engine.graphics.renderer.pipelines.StaticFirstPassUniforms
import de.hanno.hpengine.engine.graphics.shader.Program
import de.hanno.hpengine.engine.graphics.shader.ProgramManager
import de.hanno.hpengine.engine.graphics.shader.define.Define
import de.hanno.hpengine.engine.graphics.shader.define.Defines
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.graphics.state.StateRef
import de.hanno.hpengine.engine.input.Input
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.ressources.FileBasedCodeSource.Companion.toCodeSource
import net.miginfocom.swing.MigLayout
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JPanel

class ExtensibleDeferredRenderer(
    val window: Window<OpenGl>,
    val backend: Backend<OpenGl>,
    val config: Config,
    val deferredRenderingBuffer: DeferredRenderingBuffer,
    val renderStateManager: RenderStateManager,
    val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    extensions: List<DeferredRenderExtension<OpenGl>>
) : RenderSystem, Backend<OpenGl> {
    override lateinit var artemisWorld: World
    private val allExtensions: List<DeferredRenderExtension<OpenGl>> = extensions.distinct()
    private val extensions: List<DeferredRenderExtension<OpenGl>>
        get() = deferredRenderExtensionConfig.run { allExtensions.filter { it.enabled } }

    override val sharedRenderTarget = deferredRenderingBuffer.gBuffer

    val combinePassExtension = CombinePassRenderExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)
    val postProcessingExtension = PostProcessingExtension(config, backend.programManager, textureManager, backend.gpuContext, deferredRenderingBuffer)

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticFirstPassUniforms(gpuContext)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define.getDefine("ANIMATED", true)),
        AnimatedFirstPassUniforms(gpuContext)
    )

    private val useIndirectRendering
        get() = config.performance.isIndirectRendering && gpuContext.isSupported(BindlessTextures)
    private val shouldBeSkippedForDirectRendering: RenderBatch.(Camera) -> Boolean = {
        if (useIndirectRendering) !hasOwnProgram else false
    }

    val pipeline: StateRef<DirectPipeline> = renderStateManager.renderState.registerState {
        object : DirectPipeline(config, gpuContext, shouldBeSkippedForDirectRendering) {
            override fun beforeDrawAnimated(
                renderState: RenderState,
                program: Program<AnimatedFirstPassUniforms>,
                renderCam: Camera
            ) {
                super.beforeDrawAnimated(renderState, program, renderCam)
                customBeforeDraw()
            }

            override fun beforeDrawStatic(
                renderState: RenderState,
                program: Program<StaticFirstPassUniforms>,
                renderCam: Camera
            ) {
                super.beforeDrawStatic(renderState, program, renderCam)
                customBeforeDraw()
            }

            private fun customBeforeDraw() {
                deferredRenderingBuffer.use(gpuContext, false)
                gpuContext.cullFace = true
                gpuContext.cullMode = CullMode.BACK
                gpuContext.depthMask = true
                gpuContext.depthTest = true
                gpuContext.depthFunc = GlDepthFunc.LESS
                gpuContext.blend = false
            }
        }
    }
    val indirectPipeline: StateRef<IndirectPipeline> = renderStateManager.renderState.registerState {
        object : IndirectPipeline(config, gpuContext) {
            override fun beforeDrawAnimated(
                renderState: RenderState,
                program: Program<AnimatedFirstPassUniforms>,
                renderCam: Camera
            ) {
                super.beforeDrawAnimated(renderState, program, renderCam)
                customBeforeDraw()
            }

            override fun beforeDrawStatic(
                renderState: RenderState,
                program: Program<StaticFirstPassUniforms>,
                renderCam: Camera
            ) {
                super.beforeDrawStatic(renderState, program, renderCam)
                customBeforeDraw()
            }

            private fun customBeforeDraw() {
                deferredRenderingBuffer.use(gpuContext, false)
                gpuContext.cullFace = true
                gpuContext.depthMask = true
                gpuContext.depthTest = true
                gpuContext.depthFunc = GlDepthFunc.LESS
                gpuContext.blend = false
            }
        }
    }

    override val eventBus
        get() = backend.eventBus
    override val gpuContext: GpuContext<OpenGl>
        get() = backend.gpuContext
    override val programManager: ProgramManager<OpenGl>
        get() = backend.programManager
    override val textureManager: TextureManager
        get() = backend.textureManager
    override val input: Input
        get() = backend.input
    override val addResourceContext: AddResourceContext
        get() = backend.addResourceContext

    override suspend fun update(scene: Scene, deltaSeconds: Float) {
        val currentWriteState = renderStateManager.renderState.currentWriteState

        preparePipelines(currentWriteState)

        extensions.forEach { it.update(scene, deltaSeconds) }
    }

    private fun preparePipelines(currentWriteState: RenderState) {
        currentWriteState.customState[pipeline].prepare(currentWriteState, currentWriteState.camera)
        if (useIndirectRendering) {
            currentWriteState.customState[indirectPipeline].prepare(currentWriteState, currentWriteState.camera)
        }
    }

    override fun extract(scene: Scene, renderState: RenderState, world: World) {
        extensions.forEach { it.extract(scene, renderState, world) }
    }

    override fun render(result: DrawResult, renderState: RenderState): Unit = profiled("DeferredRendering") {
        actualRender(renderState, result)
    }

    private fun actualRender(renderState: RenderState, result: DrawResult) {
        gpuContext.depthMask = true
        deferredRenderingBuffer.use(gpuContext, true)

        profiled("FirstPass") {

            profiled("MainPipeline") {
                if (useIndirectRendering) {
                    renderState[indirectPipeline].draw(
                        renderState,
                        simpleColorProgramStatic,
                        simpleColorProgramAnimated,
                        result.firstPassResult
                    )
                }

                renderState[pipeline].draw(
                    renderState,
                    simpleColorProgramStatic,
                    simpleColorProgramAnimated,
                    result.firstPassResult
                )
            }

            for (extension in extensions) {
                profiled(extension.javaClass.simpleName) {
                    extension.renderFirstPass(backend, gpuContext, result.firstPassResult, renderState)
                }
            }
        }
        profiled("SecondPass") {
            profiled("HalfResolution") {
                deferredRenderingBuffer.halfScreenBuffer.use(gpuContext, true)
                for (extension in extensions) {
                    extension.renderSecondPassHalfScreen(renderState, result.secondPassResult)
                }
            }
            deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, true)
            for (extension in extensions) {
                profiled(extension.javaClass.simpleName) {
                    deferredRenderingBuffer.lightAccumulationBuffer.use(gpuContext, false)
                    extension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                }
            }
        }
        window.frontBuffer.use(gpuContext, false)
        combinePassExtension.renderCombinePass(renderState)
        deferredRenderingBuffer.use(gpuContext, false)

        runCatching {
            if (config.effects.isEnablePostprocessing) {
                // TODO This has to be implemented differently, so that
                // it is written to the final texture somehow
                profiled("PostProcessing") {
                    throw IllegalStateException("Render me to final map")
                    postProcessingExtension.renderSecondPassFullScreen(renderState, result.secondPassResult)
                }
            } else {
    //                textureRenderer.drawToQuad(deferredRenderingBuffer.finalBuffer, deferredRenderingBuffer.lightAccumulationMapOneId)
            }
        }.onFailure {
            println("Not able to render texture")
        }
    }

    override fun renderEditor(result: DrawResult, renderState: RenderState) {
        extensions.forEach { it.renderEditor(renderState, result) }
    }

    override fun afterSetScene(currentScene: Scene) {
        extensions.forEach { it.afterSetScene(currentScene) }
    }
}

class DeferredRenderExtensionConfig(val renderExtensions: List<DeferredRenderExtension<*>>) {
    private val renderSystemsEnabled = renderExtensions.distinct().associateWith { true }.toMutableMap()
    var DeferredRenderExtension<*>.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
        }
}

class DeferredRenderExtensionsConfigPanel(val renderSystemsConfig: DeferredRenderExtensionConfig) : ConfigExtension {
    override val panel: JPanel = with(renderSystemsConfig) {
        JPanel().apply {
            border = BorderFactory.createTitledBorder("RenderSystems")
            layout = MigLayout("wrap 1")

            renderSystemsConfig.renderExtensions.forEach { renderExtension ->
                add(JCheckBox(renderExtension::class.simpleName).apply {
                    isSelected = renderExtension.enabled
                    addActionListener {
                        renderExtension.enabled = !renderExtension.enabled
                    }
                })
            }
        }
    }
}