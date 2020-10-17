package de.hanno.hpengine.engine.graphics

import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.backend.gpuContext
import de.hanno.hpengine.engine.backend.input
import de.hanno.hpengine.engine.graphics.renderer.SimpleTextureRenderer
import de.hanno.hpengine.engine.graphics.state.RenderState
import de.hanno.hpengine.engine.graphics.state.RenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.SimpleRenderStateRecorder
import de.hanno.hpengine.engine.graphics.state.multithreading.TripleBuffer
import de.hanno.hpengine.engine.manager.Manager
import de.hanno.hpengine.engine.model.material.MaterialManager
import de.hanno.hpengine.engine.scene.Scene
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RenderStateManager(renderStateFactory: () -> RenderState) {
    val renderState: TripleBuffer<RenderState> = TripleBuffer(renderStateFactory,
            { currentStaging, currentRead -> currentStaging.cycle < currentRead.cycle })
}

class RenderManager(val engineContext: EngineContext, // TODO: Make generic
                    val renderStateManager: RenderStateManager = engineContext.renderStateManager,
                    val materialManager: MaterialManager = engineContext.materialManager) : Manager {

    val deferredRenderingBuffer = engineContext.deferredRenderingBuffer
    private val textureRenderer = SimpleTextureRenderer(engineContext, deferredRenderingBuffer.colorReflectivenessTexture)

    inline val renderState: TripleBuffer<RenderState>
        get() = renderStateManager.renderState
    private var lastFrameTime = 0L
    val fpsCounter = FPSCounter()

    var recorder: RenderStateRecorder = SimpleRenderStateRecorder(engineContext.input)

    override fun beforeSetScene(currentScene: Scene, nextScene: Scene) = clear()

    fun finishCycle(scene: Scene, deltaSeconds: Float) {
        renderState.currentWriteState.deltaSeconds = deltaSeconds
        engineContext.extract(scene, renderState.currentWriteState)
        renderState.swapStaging()
    }
    init {
        var lastTimeSwapped = true
        val runnable = Runnable {
            try {
                renderState.startRead()

                if (lastTimeSwapped) {
                    recorder.add(renderState.currentReadState)
                    val drawResult = renderState.currentReadState.latestDrawResult.apply { reset() }

                    profiled("renderSystems") {
                        engineContext.renderSystems.forEach {
                            it.render(drawResult, renderState.currentReadState)
                        }
                    }

                    profiled("finishFrame") {
                        engineContext.gpuContext.finishFrame(renderState.currentReadState)
                        engineContext.renderSystems.forEach {
                            it.afterFrameFinished()
                        }
                    }

                    textureRenderer.drawToQuad(engineContext.window.frontBuffer, deferredRenderingBuffer.finalMap)

                    profiled("checkCommandSyncs") {
                        engineContext.gpuContext.checkCommandSyncs()
                    }

                    GPUProfiler.dump()

                    lastFrameTime = System.currentTimeMillis()
                    fpsCounter.update()

                }
                lastTimeSwapped = renderState.stopRead()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        GlobalScope.launch {
            while(true) {
                engineContext.gpuContext.invoke(block = {
                    runnable.run()
                })
                // https://bugs.openjdk.java.net/browse/JDK-4852178
                // TODO: Remove this delay if possible anyhow, this is just so that the editor is not that unresponsive because of canvas locking
                if(isUnix) {
                    delay(5)
                }
            }
        }
    }

    override suspend fun update(scene: Scene, deltaSeconds: Float) {

        this@RenderManager.engineContext.renderSystems.forEach {
            it.run { update(scene, deltaSeconds) }
        }
    }

    val deltaMs
        get() = System.currentTimeMillis().toDouble() - lastFrameTime.toDouble()

    val deltaS
        get() = deltaMs / 1000.0

    fun getCurrentFPS() = fpsCounter.fps

    fun getMsPerFrame() = fpsCounter.msPerFrame

}

inline fun <T> profiled(name: String, action: () -> T): T {
    val task = GPUProfiler.start(name)
    val result = action()
    task?.end()
    return result
}


private val OS = System.getProperty("os.name").toLowerCase()
private val isUnix = OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0