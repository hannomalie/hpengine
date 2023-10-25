package de.hanno.hpengine.graphics.editor

import de.hanno.hpengine.graphics.RenderManager
import de.hanno.hpengine.graphics.RenderMode
import de.hanno.hpengine.graphics.imgui.dsl.TabBar
import de.hanno.hpengine.graphics.imgui.dsl.TreeNode.text
import de.hanno.hpengine.graphics.profiling.GPUProfiler
import imgui.ImGui

fun TabBar.renderTab(
    editor: ImGuiEditor,
    gpuProfiler: GPUProfiler,
    renderManager: RenderManager
) {
    tab("Render") {
        text("FPS: ${editor.fpsCounter.fps}")
        text(gpuProfiler.currentTimings)

        val renderMode = renderManager.renderMode
        if (ImGui.button("Use ${if (renderMode is RenderMode.Normal) "Single Step" else "Normal"}")) {
            renderManager.renderMode = when (renderMode) {
                RenderMode.Normal -> RenderMode.SingleFrame()
                is RenderMode.SingleFrame -> RenderMode.Normal
            }
        }
        if (renderMode is RenderMode.SingleFrame) {
            if (ImGui.button("Step")) {
                renderMode.frameRequested.getAndSet(true)
            }
        }
    }
}

