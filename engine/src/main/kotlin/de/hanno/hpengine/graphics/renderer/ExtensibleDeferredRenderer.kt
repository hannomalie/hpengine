package de.hanno.hpengine.graphics.renderer

import com.artemis.World
import de.hanno.hpengine.artemis.EntitiesStateHolder
import de.hanno.hpengine.artemis.EnvironmentProbesStateHolder
import de.hanno.hpengine.artemis.PrimaryCameraStateHolder

import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.renderer.constants.DepthFunc
import de.hanno.hpengine.graphics.renderer.drawstrategy.DeferredRenderingBuffer
import de.hanno.hpengine.graphics.renderer.drawstrategy.extensions.DeferredRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.CombinePassRenderExtension
import de.hanno.hpengine.graphics.renderer.extensions.PostProcessingExtension
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.shader.define.Define
import de.hanno.hpengine.graphics.shader.define.Defines
import de.hanno.hpengine.graphics.state.RenderState
import de.hanno.hpengine.graphics.state.RenderSystem
import de.hanno.hpengine.graphics.state.StateRef
import de.hanno.hpengine.ressources.FileBasedCodeSource.Companion.toCodeSource
import de.hanno.hpengine.graphics.*
import de.hanno.hpengine.graphics.renderer.pipelines.*
import de.hanno.hpengine.graphics.texture.TextureManager

context(GpuContext, RenderStateContext)
class ExtensibleDeferredRenderer(
    private val window: Window,
    private val config: Config,
    private val deferredRenderingBuffer: DeferredRenderingBuffer,
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val programManager: ProgramManager,
    private val textureManager: TextureManager,
    extensions: List<DeferredRenderExtension>,
    private val entitiesStateHolder: EntitiesStateHolder,
    private val environmentProbesStateHolder: EnvironmentProbesStateHolder,
    private val primaryCameraStateHolder: PrimaryCameraStateHolder,
) : RenderSystem {
    override lateinit var artemisWorld: World
    private val allExtensions: List<DeferredRenderExtension> = extensions.distinct()
    private val extensions: List<DeferredRenderExtension>
        get() = deferredRenderExtensionConfig.run { allExtensions.filter { it.enabled } }.sortedBy { it.renderPriority }

    override val sharedRenderTarget = deferredRenderingBuffer.gBuffer

    val combinePassExtension = CombinePassRenderExtension(config, programManager, textureManager, this@GpuContext, deferredRenderingBuffer, environmentProbesStateHolder, primaryCameraStateHolder)
    val postProcessingExtension = PostProcessingExtension(config, programManager, textureManager, this@GpuContext, deferredRenderingBuffer, primaryCameraStateHolder)

    val simpleColorProgramStatic = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(),
        StaticFirstPassUniforms()
    )

    val simpleColorProgramAnimated = programManager.getProgram(
        config.engineDir.resolve("shaders/first_pass_vertex.glsl").toCodeSource(),
        config.engineDir.resolve("shaders/first_pass_fragment.glsl").toCodeSource(),
        null,
        Defines(Define("ANIMATED", true)),
        AnimatedFirstPassUniforms()
    )

    private val useIndirectRendering
        get() = config.performance.isIndirectRendering && isSupported(BindlessTextures)

    val indirectPipeline: StateRef<GPUCulledPipeline> = renderState.registerState {
        GPUCulledPipeline(config, programManager, textureManager, deferredRenderingBuffer, true, entitiesStateHolder, primaryCameraStateHolder)
    }
    private val staticDirectPipeline: StateRef<DirectFirstPassPipeline> = renderState.registerState {
        object: DirectFirstPassPipeline(config, simpleColorProgramStatic, entitiesStateHolder, primaryCameraStateHolder) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                this[entitiesStateHolder.entitiesState].renderBatchesStatic.filterNot { it.canBeRenderedInIndirectBatch }
            } else {
                this[entitiesStateHolder.entitiesState].renderBatchesStatic.filterNot {
                    it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
                }
            }
        }
    }
    private val animatedDirectPipeline: StateRef<DirectFirstPassPipeline> = renderState.registerState {
        object: DirectFirstPassPipeline(config, simpleColorProgramAnimated,entitiesStateHolder, primaryCameraStateHolder) {
            override fun RenderState.extractRenderBatches() = if(useIndirectRendering) {
                this[entitiesStateHolder.entitiesState].renderBatchesAnimated.filterNot { it.canBeRenderedInIndirectBatch }
            } else {
                this[entitiesStateHolder.entitiesState].renderBatchesAnimated.filterNot {
                    it.shouldBeSkipped(this[primaryCameraStateHolder.camera])
                }
            }

            override fun RenderState.selectVertexIndexBuffer() = this[entitiesStateHolder.entitiesState].vertexIndexBufferAnimated
        }
    }

    override fun update(deltaSeconds: Float) {
        val currentWriteState = renderState.currentWriteState

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

    override fun extract(renderState: RenderState, world: World) {
        extensions.forEach { it.extract(renderState, world) }
    }

    override fun render(renderState: RenderState): Unit = profiled("DeferredRendering") {
        actualRender(renderState)
    }

    private fun actualRender(renderState: RenderState) {

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

        window.frontBuffer.use(false)
        combinePassExtension.renderCombinePass(renderState)
        deferredRenderingBuffer.use(false)

        runCatching {
            if (config.effects.isEnablePostprocessing) {
                // TODO This has to be implemented differently, so that
                // it is written to the final texture somehow
                profiled("PostProcessing") {
                    throw IllegalStateException("Render me to final map")
                    postProcessingExtension.renderSecondPassFullScreen(renderState)
                }
            } else {
    //                textureRenderer.drawToQuad(deferredRenderingBuffer.finalBuffer, deferredRenderingBuffer.lightAccumulationMapOneId)
            }
        }.onFailure {
            println("Not able to render texture")
        }
    }

    override fun renderEditor(renderState: RenderState) {
        extensions.forEach { it.renderEditor(renderState) }
    }

}

class DeferredRenderExtensionConfig(val renderExtensions: List<DeferredRenderExtension>) {
    private val renderSystemsEnabled = renderExtensions.distinct().associateWith { true }.toMutableMap()
    var DeferredRenderExtension.enabled: Boolean
        get() = renderSystemsEnabled[this]!!
        set(value) {
            renderSystemsEnabled[this] = value
        }
}
