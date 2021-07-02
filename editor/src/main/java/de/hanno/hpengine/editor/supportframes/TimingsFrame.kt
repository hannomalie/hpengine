package de.hanno.hpengine.editor.supportframes

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.TextArea
import javax.swing.JFrame
import javax.swing.JPanel

class TimingsFrame : JFrame("Timings") {
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
//            engine.renderSystems.add(object : RenderSystem {
//                override fun afterFrameFinished() {
//                    engine.addResourceContext.launch {
//                        SwingUtils.invokeLater {
//                            if(engine.engineContext.config.profiling.showFps) {
//                                val fpsCounter = engine.renderManager.fpsCounter
//                                val fpsInfo = "${fpsCounter.fps.toInt()} fps @ ${fpsCounter.msPerFrame} ms"
//                                val cpsInfo = "${engine.cpsCounter.fps.toInt()} cps @ ${engine.cpsCounter.msPerFrame} ms"
//                                textArea.text = "HPEngine | $fpsInfo | $cpsInfo\n\n"
//                            } else {
//                                textArea.text = ""
//                            }
//
//                            if(GPUProfiler.dumpAveragesRequested) {
//                                var drawResult = engine.engineContext.renderStateManager.renderState.currentReadState.latestDrawResult.toString()
//                                if (GPUProfiler.dumpAveragesRequested) {
//                                    drawResult += GPUProfiler.currentAverages
//                                }
//                                textArea.text += GPUProfiler.currentAverages + "\n\n"
//                            }
//                            if (GPUProfiler.porfiling) {
//                                textArea.text += GPUProfiler.currentTimings
//                            }
//                        }
//                    }
//                }
//            })
        })

        frame.isVisible = true
    }
}