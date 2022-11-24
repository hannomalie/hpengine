package de.hanno.hpengine.graphics.imgui.editor

import de.hanno.hpengine.graphics.RenderMode
import de.hanno.hpengine.graphics.imgui.dsl.TabBar
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.renderer.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.pipelines.GPUCulledPipeline
import de.hanno.hpengine.stopwatch.GPUProfiler
import de.hanno.hpengine.util.Util
import de.hanno.hpengine.stopwatch.ProfilingTask
import imgui.ImGui
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.lwjgl.opengl.GL11

context(Window, ImGuiEditor)
fun TabBar.renderTab() {
    tab("Render") {
        text("FPS: ${fpsCounter.fps}") {}
        text(GPUProfiler.currentTimings)

        val renderManager = artemisWorld.getSystem(de.hanno.hpengine.graphics.RenderManager::class.java)!!
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

        renderManager.renderSystems
            .firstIsInstanceOrNull<ExtensibleDeferredRenderer>()?.apply {
                val currentReadState = renderStateManager.renderState.currentReadState
                var counter = 0
                when (val indirectPipeline = currentReadState[indirectPipeline]) {
                    is GPUCulledPipeline -> {
                        GL11.glFinish()
                        val commandOrganization = indirectPipeline.commandOrganizationStatic
                        val batchCount = commandOrganization.filteredRenderBatches.size
                        val commandCount = commandOrganization.drawCountsCompacted.buffer.getInt(0)
                        val visibilityCount = if (commandCount == 0) {
                            0
                        } else {
                            var result = 0
                            indirectPipeline.commandOrganizationStatic.commandsCompacted.typedBuffer.forEach(
                                commandCount
                            ) {
                                result += it.instanceCount
                            }
                            result
                        }
                        ImGui.text("Visible instances: $visibilityCount")

                        val commandOffsetsCulled = Util.toString(
                            indirectPipeline.commandOrganizationStatic.offsetsCompacted.buffer.asIntBuffer(),
                            commandCount,
                            1
                        ).take(15)
                        ImGui.text("Command offsets culled: $commandOffsetsCulled")

                        ImGui.text("Draw commands: $batchCount")
                        ImGui.text("Draw commands compacted: $commandCount")
                        commandOrganization.commandsCompacted.typedBuffer.forEach(batchCount) {
                            if (counter < commandCount) {
                                val entityOffset = commandOrganization.offsetsCompacted.typedBuffer.forIndex(counter) { it.value }
                                ImGui.text("$entityOffset - ")
                                ImGui.sameLine()
                                ImGui.text(it.print())
                            } else {
                                ImGui.text("_ - ")
                                ImGui.sameLine()
                                ImGui.text(it.print())
                            }
                            counter++
                        }
                    }
                }
            }
    }
}