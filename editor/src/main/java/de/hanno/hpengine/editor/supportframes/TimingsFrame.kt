package de.hanno.hpengine.editor.supportframes

import de.hanno.hpengine.editor.SwingUtils
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.graphics.state.RenderSystem
import de.hanno.hpengine.util.stopwatch.GPUProfiler
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.TextArea
import javax.swing.JFrame
import javax.swing.JPanel

class TimingsFrame(engine: Engine<*>): JFrame("Timings") {
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

            engine.renderSystems.add(object : RenderSystem {
                override fun afterFrameFinished() {
                    if (GPUProfiler.PROFILING_ENABLED) {
                        SwingUtils.invokeLater {
                            var drawResult = engine.renderManager.renderState.currentReadState.latestDrawResult.toString()
                            if (GPUProfiler.DUMP_AVERAGES) {
                                drawResult += GPUProfiler.currentAverages
                            }
                            val fpsCounter = engine.renderManager.fpsCounter
                            textArea.text = "HPEngine - ${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms\n\n" +
                                    GPUProfiler.currentAverages + "\n\n" + GPUProfiler.currentTimings
                        }
                    } else if(engine.config.profiling.showFps) {
                        SwingUtils.invokeLater {
                            val fpsCounter = engine.renderManager.fpsCounter
                            textArea.text = "HPEngine - ${fpsCounter.fps.toInt()} fps - ${fpsCounter.msPerFrame} ms\n\n"
                        }
                    }
                }
            })
        })

        frame.isVisible = true
    }
}