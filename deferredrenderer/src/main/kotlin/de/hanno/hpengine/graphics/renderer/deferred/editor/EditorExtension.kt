package de.hanno.hpengine.graphics.renderer.deferred.editor

import com.artemis.Component
import com.artemis.utils.Bag
import de.hanno.hpengine.graphics.GraphicsApi
import de.hanno.hpengine.graphics.RenderSystemsConfig
import de.hanno.hpengine.graphics.editor.ImGuiEditor
import de.hanno.hpengine.graphics.editor.extension.EditorExtension
import de.hanno.hpengine.graphics.editor.select.MaterialSelection
import de.hanno.hpengine.graphics.editor.select.Selection
import de.hanno.hpengine.graphics.imgui.dsl.Window
import de.hanno.hpengine.graphics.renderer.deferred.DeferredRenderExtensionConfig
import de.hanno.hpengine.graphics.renderer.deferred.ExtensibleDeferredRenderer
import de.hanno.hpengine.graphics.renderer.pipelines.GPUCulledPipeline
import de.hanno.hpengine.graphics.state.RenderStateContext
import imgui.ImGui
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.koin.core.annotation.Single
import struktgen.api.forEach
import struktgen.api.forIndex
import java.nio.IntBuffer


data class DeferredRendererSelection(val renderer: ExtensibleDeferredRenderer): Selection
@Single(binds = [EditorExtension::class])
class DeferredRendererEditorExtension(
    private val deferredRenderExtensionConfig: DeferredRenderExtensionConfig,
    private val renderStateContext: RenderStateContext,
    private val graphicsApi: GraphicsApi,
    private val renderSystemsConfig: Lazy<RenderSystemsConfig>,
): EditorExtension {

    override fun ImGuiEditor.renderLeftPanelTopLevelNode() {
        Window.treeNode("DeferredRenderer") {
            renderSystemsConfig.value.renderSystems.firstIsInstanceOrNull<ExtensibleDeferredRenderer>()?.let { it ->
                text("DeferredRenderer") {
                    selectOrUnselect(DeferredRendererSelection(it))
                }
            }
        }
    }
    override fun getSelection(any: Any, components: Bag<Component>?) = if(any is ExtensibleDeferredRenderer) {
        DeferredRendererSelection(any)
    } else null

    override fun Window.renderRightPanel(selection: Selection?) = if(selection is DeferredRendererSelection) {
        deferredRenderExtensionConfig.run {
            renderExtensions.forEach {
                if (ImGui.checkbox(it.javaClass.simpleName, it.enabled)) {
                    it.enabled = !it.enabled
                }
            }
        }

        val renderCommands = false
        if(renderCommands) {
            renderSystemsConfig.value.renderSystems.firstIsInstanceOrNull<ExtensibleDeferredRenderer>()?.apply {
                val currentReadState = renderStateContext.renderState.currentReadState
                var counter = 0
                when (val indirectPipeline = currentReadState[indirectPipeline]) {
                    is GPUCulledPipeline -> {
                        graphicsApi.finish()
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

                        val commandOffsetsCulled =
                            indirectPipeline.commandOrganizationStatic.offsetsCompacted.buffer.asIntBuffer().print(
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
        true
    } else false

}

private fun IntBuffer.print(columns: Int, rows: Int): String {
    rewind()
    val builder = StringBuilder()
    var columnCounter = 1
    var rowCounter = 0
    while (columns > 0 && hasRemaining() && rowCounter < rows) {
        builder.append(get())
        builder.append(" ")
        if (columnCounter % columns == 0) {
            builder.append(System.lineSeparator())
            rowCounter++
        }
        columnCounter++
    }
    rewind()
    return builder.toString()
}