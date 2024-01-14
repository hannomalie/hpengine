package de.hanno.hpengine.graphics.renderer.deferred

import com.artemis.BaseSystem
import de.hanno.hpengine.model.EntitiesStateHolder
import de.hanno.hpengine.graphics.state.PrimaryCameraStateHolder
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.constants.DepthFunc
import de.hanno.hpengine.graphics.constants.RenderingMode
import de.hanno.hpengine.graphics.renderer.deferred.extensions.CombinePassRenderExtension
import de.hanno.hpengine.graphics.renderer.deferred.extensions.PostProcessingExtension
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.RenderSystem
import de.hanno.hpengine.graphics.constants.Facing
import de.hanno.hpengine.graphics.envprobe.EnvironmentProbesStateHolder
import de.hanno.hpengine.graphics.feature.BindlessTextures
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.deferred.extensions.PostProcessingExtensionProxy
import de.hanno.hpengine.graphics.renderer.forward.AnimatedFirstPassUniforms
import de.hanno.hpengine.graphics.renderer.forward.StaticFirstPassUniforms
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.graphics.texture.TextureManager
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.model.EntityBuffer
import de.hanno.hpengine.model.DefaultBatchesSystem
import de.hanno.hpengine.model.material.MaterialSystem
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.koin.core.annotation.Single

@Single(binds = [RenderSystem::class, BaseSystem::class, PrimaryRenderer::class])
class ExtensibleDeferredRenderer(
    private val graphicsApi: GraphicsApi,
    private val renderStateContext: RenderStateContext,
    private val gpuProfiler: GPUProfiler,
    private val window: Window,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val programManager: ProgramManager,
    private val textureManager: TextureManager,
    extensions: List<DeferredRenderExtension>,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val entityBuffer: EntityBuffer,
    private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
    private val defaultBatchesSystem: DefaultBatchesSystem,
    private val materialSystem: MaterialSystem,
): PrimaryRenderer, BaseSystem() {
    override val finalOutput = DeferredFinalOutput(deferredRenderingBuffer.finalMap, 0, this)
    override val renderPriority: Int get() = 10

    private val allExtensions: List<DeferredRenderExtension> = extensions.distinct()
    private val extensions: List<DeferredRenderExtension>
        get() = deferredRenderExtensionConfig.run { allExtensions.filter { it.enabled } }.sortedBy { it.renderPriority }

    override val sharedRenderTarget = deferredRenderingBuffer.gBuffer

    val combinePassExtension = CombinePassRenderExtension(config, programManager, textureManager, graphicsApi, deferredRenderingBuffer, environmentProbesStateHolder, primaryCameraStateHolder)
    val postProcessingExtension = PostProcessingExtension(config, programManager, textureManager, graphicsApi, deferredRenderingBuffer, primaryCameraStateHolder)
    val postProcessingExtensionProxy = extensions.firstIsInstance<PostProcessingExtensionProxy>()

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticFirstPassUniforms(graphicsApi)
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true)),
        AnimatedFirstPassUniforms(graphicsApi)
    )

    private val useIndirectRendering
        get() = config.performance.isIndirectRendering && graphicsApi.isSupported(BindlessTextures)

    val indirectPipeline: StateRef<GPUCulledPipeline> = renderStateContext.renderState.registerState {
        GPUCulledPipeline(graphicsApi, config, programManager, textureManager, deferredRenderingBuffer, true, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem)
    }
    private val staticDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramStatic, entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem, textureManager.defaultTexture) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                this[defaultBatchesSystem.renderBatchesStatic].filterNot { it.canBeRenderedInIndirectBatch }
            } else {
                this[defaultBatchesSystem.renderBatchesStatic].filterNot {
                    it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
                }
            }
        }
    }
    private val animatedDirectPipeline: StateRef<DirectPipeline> = renderStateContext.renderState.registerState {
        object: DirectPipeline(graphicsApi, config, simpleColorProgramAnimated,entitiesStateHolder, entityBuffer, primaryCameraStateHolder, defaultBatchesSystem, materialSystem, textureManager.defaultTexture) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                this[defaultBatchesSystem.renderBatchesAnimated].filterNot { it.canBeRenderedInIndirectBatch }
            } else {
                this[defaultBatchesSystem.renderBatchesAnimated].filterNot {
                    it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
                }
            }

            override fun RenderState.selectVertexIndexBuffer() = this[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderStateContext.renderState.currentWriteState

        preparePipelines(currentWriteState)

        extensions.forEach { it.update(deltaSeconds) }
    }

    private fun preparePipelines(currentWriteState: RenderState) {
        currentWriteState[staticDirectPipeline].prepare(currentWriteState)
        currentWriteState[animatedDirectPipeline].prepare(currentWriteState)
        if (useIndirectRendering) {
            currentWriteState.customState[indirectPipeline].prepare(currentWriteState)
        }
    }

    override fun extract(renderState: RenderState) {
        if(world == null) return
        extensions.forEach { it.extract(renderState, world) }
    }

    override fun render(renderState: RenderState): Unit = graphicsApi.run {
        profiled("DeferredRendering") {
            actualRender(renderState)
        }
    }

    private fun actualRender(renderState: RenderState): Unit = graphicsApi.run {

        for (extension in extensions) {
            profiled(extension.javaClass.simpleName) {
                extension.renderZeroPass(renderState)
            }
        }

        if (useIndirectRendering) {
            renderState[indirectPipeline].copyDepthTexture()
        }

        cullFace = true
        depthMask = true
        depthTest = true
        depthFunc = DepthFunc.LESS
        blend = false
        deferredRenderingBuffer.use(true)

        profiled("FirstPass") {

            profiled("MainPipeline") {
                renderState[staticDirectPipeline].draw(renderState)
                renderState[animatedDirectPipeline].draw(renderState)
            }

            if (useIndirectRendering) {
                renderState[indirectPipeline].draw(
                    renderState,
                    simpleColorProgramStatic,
                    simpleColorProgramAnimated
                )
            }
            for (extension in extensions) {
                profiled(extension.javaClass.simpleName) {
                    extension.renderFirstPass(renderState)
                }
            }
        }

        polygonMode(Facing.FrontAndBack, RenderingMode.Fill)
        profiled("SecondPass") {
            profiled("HalfResolution") {
                profiled("Use rendertarget") {
                    deferredRenderingBuffer.halfScreenBuffer.use(true)
                }
                for (extension in extensions) {
                    extension.renderSecondPassHalfScreen(renderState)
                }
            }

            profiled("FullResolution") {
                profiled("Use rendertarget") {
                    deferredRenderingBuffer.lightAccumulationBuffer.use(true)
                }
                for (extension in extensions) {
                    profiled(extension.javaClass.simpleName) {
                        extension.renderSecondPassFullScreen(renderState)
                    }
                }
            }
        }

        val postProcessingEnabled = deferredRenderExtensionConfig.run { postProcessingExtensionProxy.enabled }
        if(!postProcessingEnabled) {
            window.frontBuffer.use(graphicsApi, false)
            deferredRenderingBuffer.exposureBuffer.buffer.putFloat(0, 1.0f)
        }
        combinePassExtension.renderCombinePass(renderState)
        if(postProcessingEnabled) {
            postProcessingExtension.renderSecondPassFullScreen(renderState)
        }
        deferredRenderingBuffer.use(false)
    }

    override fun processSystem() { }
}

@Single
class DeferredRenderExtensionConfig(val _renderExtensions: List<DeferredRenderExtension>) {
    val renderExtensions = _renderExtensions.distinct()
    private val renderSystemsEnabled = renderExtensions.distinct().associateWith { true }.toMutableMap()
    var DeferredRenderExtension.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
        }
}
