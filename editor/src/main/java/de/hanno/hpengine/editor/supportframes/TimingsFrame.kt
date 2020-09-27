package de.hanno.hpengine.editor.supportframes

import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.engine.backend.EngineContext
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.TextArea
import javax.swing.JFrame
import javax.swing.JPanel

class TimingsFrame(engineContext: EngineContext): JFrame("Timings") {
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
//            engineContext.renderSystems.add(object : RenderSystem {
//                override fun afterFrameFinished() {
//                    if (GPUProfiler.PROFILING_ENABLED) {
//                        SwingUtils.invokeLater {
//                            var drawResult = engineContext.renderManager.renderState.currentReadState.latestDrawResult.toString()
//                            if (GPUProfiler.DUMP_AVERAGES) {
//                                drawResult += GPUProfiler.currentAverages
//                            }
//                            val fpsCounter = engineContext.renderManager.fpsCounter
//                            val fpsInfo = "${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms"
//                            val cpsInfo = "${engineContext.cpsCounter.fps.toInt()} cps - ${engineContext.cpsCounter.msPerFrame} ms"
//                            textArea.text = "HPEngine | $fpsInfo | $cpsInfo\n\n" +
//                                    GPUProfiler.currentAverages + "\n\n" + GPUProfiler.currentTimings
//                        }
//                    } else if(engineContext.config.profiling.showFps) {
//                        SwingUtils.invokeLater {
//                            val fpsCounter = engineContext.renderManager.fpsCounter
//                            val fpsInfo = "${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms"
//                            val cpsInfo = "${engineContext.cpsCounter.fps.toInt()} cps - ${engineContext.cpsCounter.msPerFrame} ms"
//                            textArea.text = "HPEngine | $fpsInfo | $cpsInfo"
//                        }
//                    }
//                }
//            })
        })

        frame.isVisible = true
    }
}