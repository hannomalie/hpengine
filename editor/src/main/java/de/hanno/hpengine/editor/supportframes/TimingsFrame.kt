package de.hanno.hpengine.editor.supportframes

import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.engine.renderSystems
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.TextArea
import javax.swing.JFrame
import javax.swing.JPanel

class TimingsFrame(engine: Engine): JFrame("Timings") {
    init {
        size = Dimension(500, 500)
        val frame = this

        add(JPanel().apply {
            layout = BorderLayout()
            val panel = this
            val textArea = TextArea().apply {
                isEditable = false
                panel.add(this, BorderLayout.CENTER)
            }

//            TODO: Reimplement properly
            engine.renderSystems.add(object : RenderSystem {
                override fun afterFrameFinished() {
                    if (GPUProfiler.PROFILING_ENABLED) {
                        SwingUtils.invokeLater {
                            var drawResult = engine.engineContext.renderStateManager.renderState.currentReadState.latestDrawResult.toString()
                            if (GPUProfiler.DUMP_AVERAGES) {
                                drawResult += GPUProfiler.currentAverages
                            }
                            val fpsCounter = engine.renderManager.fpsCounter
                            val fpsInfo = "${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms"
                            val cpsInfo = "${engine.cpsCounter.fps.toInt()} cps - ${engine.cpsCounter.msPerFrame} ms"
                            textArea.text = "HPEngine | $fpsInfo | $cpsInfo\n\n" +
                                    GPUProfiler.currentAverages + "\n\n" + GPUProfiler.currentTimings
                        }
                    } else if(engine.engineContext.config.profiling.showFps) {
                        SwingUtils.invokeLater {
                            val fpsCounter = engine.renderManager.fpsCounter
                            val fpsInfo = "${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms"
                            val cpsInfo = "${engine.cpsCounter.fps.toInt()} cps - ${engine.cpsCounter.msPerFrame} ms"
                            textArea.text = "HPEngine | $fpsInfo | $cpsInfo"
                        }
                    }
                }
            })
        })

        frame.isVisible = true
    }
}