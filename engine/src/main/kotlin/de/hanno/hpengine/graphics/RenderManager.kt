package de.hanno.hpengine.graphics

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.lifecycle.UpdateCycle
import de.hanno.hpengine.spatial.WorldAABB
import de.hanno.hpengine.system.Extractor
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean

@Single(binds = [BaseSystem::class, RenderManager::class])
class RenderManager(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val window: Window,
    programManager: ProgramManager,
    private val renderStateContext: RenderStateContext,
    internal val renderSystemsConfig: RenderSystemsConfig,
    private val gpuProfiler: GPUProfiler,
    private val updateCycle: UpdateCycle,
) : BaseSystem() {

    val logger = LogManager.getLogger(RenderManager::class.java)

    var renderMode: RenderMode = RenderMode.Normal

    private val textureRenderer = SimpleTextureRenderer(
        graphicsApi,
        config,
        null,
        programManager,
        window.frontBuffer
    )

    fun finishCycle(deltaSeconds: Float) {
        renderStateContext.renderState.currentWriteState.deltaSeconds = deltaSeconds
        renderStateContext.renderState.swapStaging()
    }

    internal val rendering = AtomicBoolean(false)

    init {
        window.gpuExecutor.perFrameAction = ::frame
    }

    private fun frame() {
        logger.debug("frame")
        gpuProfiler.run {
            graphicsApi.run {
                rendering.getAndSet(true)
                try {
                    renderStateContext.renderState.readLocked { currentReadState ->

                        val renderSystems = profiled("determineRenderSystems") {
                            when (val renderMode = renderMode) {
                                RenderMode.Normal -> renderSystemsConfig.run { nonPrimaryRenderers.filter { it.enabled } }

                                is RenderMode.SingleFrame -> {
                                    renderSystemsConfig.run {
                                        val (singleStepSystems, continuousSystems) = allRenderSystems.filter { it.enabled }.partition {
                                            it.supportsSingleStep
                                        }
                                        val systemsToExecute = continuousSystems + if (renderMode.frameRequested.get()) {
                                            singleStepSystems
                                        } else {
                                            emptyList() // TODO: Check whether this still works
                                        }

                                        renderMode.frameRequested.getAndSet(false)
                                        systemsToExecute
                                    }
                                }
                            }
                        }

                        logger.debug("renderSystems.render")
                        profiled("renderSystems") {
                            renderSystems.groupBy { it.sharedRenderTarget }
                                .forEach { (renderTarget, renderSystems) ->
                                    val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                    profiled("use rendertarget") {
                                        renderTarget?.use(clear)
                                    }
                                    renderSystems.forEach { renderSystem ->
                                        profiled(renderSystem.javaClass.simpleName) {
                                            renderSystem.render(currentReadState)
                                        }
                                    }
                                    renderSystemsConfig.primaryRenderer.render(currentReadState)
                                }
                        }

                        logger.debug("present")
                        profiled("present") {
                            window.frontBuffer.use(graphicsApi, true)
                            val finalOutput = renderSystemsConfig.primaryRenderer.finalOutput
                            textureRenderer.drawToQuad(
                                finalOutput.texture2D,
                                mipMapLevel = finalOutput.mipmapLevel
                            )
                        }

                        profiled("checkCommandSyncs") {
                            checkCommandSyncs()
                        }

                        profiled("finishFrame") {
                            finishFrame(currentReadState)
                            renderSystems.forEach {
                                it.afterFrameFinished()
                            }
                        }
                        profiled("finish") {
                            finish()
                        }
                        logger.debug("swapBuffers")
                        profiled("swapBuffers") {
                            window.swapBuffers()
                            window.closeIfReqeusted()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    rendering.getAndSet(false)
                }
            }
        }
    }

    override fun processSystem() {
        while(config.debug.forceSingleThreadedRendering && rendering.get()) {
            Thread.onSpinWait()
        }
        renderSystemsConfig.allRenderSystems.forEach {
            it.update(world.delta)
        }
    }

    fun extract(extractors: List<Extractor>, deltaSeconds: Float) {
        val currentWriteState = renderStateContext.renderState.currentWriteState
        currentWriteState.cycle = updateCycle.cycle.get()
        currentWriteState.time = System.currentTimeMillis()

        extractors.forEach { it.extract(currentWriteState) }

        finishCycle(deltaSeconds)
    }
}
