package de.hanno.hpengine.graphics

import com.artemis.BaseSystem
import de.hanno.hpengine.config.Config
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import de.hanno.hpengine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.graphics.shader.ProgramManager
import de.hanno.hpengine.graphics.state.RenderStateContext
import de.hanno.hpengine.graphics.window.Window
import de.hanno.hpengine.lifecycle.Termination
import de.hanno.hpengine.lifecycle.UpdateCycle
import de.hanno.hpengine.system.Extractor
import org.apache.logging.log4j.LogManager
import org.koin.core.annotation.Single
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

@Single(binds = [BaseSystem::class, RenderManager::class])
class RenderManager(
    private val graphicsApi: GraphicsApi,
    private val config: Config,
    private val window: Window,
    programManager: ProgramManager,
    private val renderStateContext: RenderStateContext,
    val renderSystemsConfig: RenderSystemsConfig,
    private val gpuProfiler: GPUProfiler,
    private val updateCycle: UpdateCycle,
    private val termination: Termination,
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
        window.gpuExecutor.loopCondition = { !termination.terminationRequested.get() }
        window.gpuExecutor.afterLoop = {
            window.close()
            exitProcess(0)
        }
    }

    private fun frame() {
        logger.trace("frame")
        gpuProfiler.run {
            graphicsApi.run {

                rendering.getAndSet(true)
                try {
                    renderStateContext.renderState.readLocked { currentReadState ->
                        renderSystemsConfig.run {

                            // TODO: Reenable single step rendering
                            logger.trace("renderSystems.render")
                            profiled("renderSystems") {
                                renderSystemsConfig.renderSystemsGroupedByTarget.forEach { (renderTarget, renderSystems) ->
                                    val clear = renderSystems.any { it.requiresClearSharedRenderTarget }
                                    profiled("use rendertarget") {
                                        renderTarget?.use(clear)
                                    }
                                    renderSystems.forEach { renderSystem ->
                                        if(renderSystem.enabled) {
                                            profiled(renderSystem.javaClass.simpleName) {
                                                renderSystem.render(currentReadState)
                                            }
                                        }
                                    }
                                }
                                renderSystemsConfig.primaryRenderer.render(currentReadState)
                            }

                            logger.trace("present")
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
                                renderSystemsConfig.renderSystems.forEach {
                                    if(it.enabled) {
                                        it.afterFrameFinished()
                                    }
                                }
                            }
                            logger.trace("swapBuffers")
                            profiled("swapBuffers") {
                                window.swapBuffers()
                            }
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
        while (config.debug.forceSingleThreadedRendering && rendering.get()) {
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
