import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditor
import de.hanno.hpengine.graphics.imgui.editor.ImGuiEditorExtension
import imgui.ImGui
import imgui.type.ImBoolean

class EditorExtension: ImGuiEditorExtension {
    override fun render(imGuiEditor: ImGuiEditor) {
        de.hanno.hpengine.graphics.imgui.dsl.ImGui.run {
            window("FOOOOOO") {
                text("asdasdasd")
            }
        }
        ImGui.showDemoWindow(ImBoolean(true))
    }
}