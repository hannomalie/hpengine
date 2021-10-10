package de.hanno.hpengine.editor.supportframes

import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.graphics.RenderStateManager
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.util.fps.CPSCounter
import de.hanno.hpengine.util.fps.FPSCounter
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.TextArea
import javax.swing.JFrame
import javax.swing.JPanel

class TimingsFrame(profilingRenderSystem: ProfilingRenderSystem) : JFrame("Timings") {
    init {
        size = Dimension(500, 500)
        val frame = this

        add(JPanel().apply {
            layout = BorderLayout()
            add(profilingRenderSystem.textArea, BorderLayout.CENTER)
        })

        frame.isVisible = true
    }
}


class ProfilingRenderSystem(
    private val config: Config,
    private val addResourceContext: AddResourceContext,
    private val renderStateManager: RenderStateManager,
    private val fpsCounter: FPSCounter,
    private val cpsCounter: CPSCounter
) : RenderSystem {
    val textArea = TextArea().apply {
        isEditable = false
    }

    override fun afterFrameFinished() {

        val currentAverages = if (GPUProfiler.dumpAveragesRequested) {
            var drawResult = renderStateManager.renderState.currentReadState.latestDrawResult.toString()
            if (GPUProfiler.dumpAveragesRequested) {
                drawResult += GPUProfiler.currentAverages
            }
            GPUProfiler.currentAverages + "\n\n"
        } else ""

        val currentTimings = if (GPUProfiler.porfiling) {
            GPUProfiler.currentTimings
        } else ""

        val baseText = if (config.profiling.showFps) {
            val fpsInfo = "${fpsCounter.fps.toInt()} fps @ ${fpsCounter.msPerFrame} ms"
            val cpsInfo = "${cpsCounter.fps.toInt()} cps @ ${cpsCounter.msPerFrame} ms"
            "HPEngine | $fpsInfo | $cpsInfo\n\n"
        } else ""
        val result = baseText + currentAverages + currentTimings

        if(result.isNotEmpty()) {
            SwingUtils.invokeLater {
                textArea.text = result
            }
        }
    }
}