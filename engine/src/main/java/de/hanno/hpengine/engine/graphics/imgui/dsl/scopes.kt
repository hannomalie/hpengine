package de.hanno.hpengine.engine.graphics.imgui.dsl
import imgui.type.ImBoolean
import imgui.ImGui as IG

object ImGui {
    inline fun window(title: String, windowFlags: Int = 0, block: Window.() -> Unit) {
        if(IG.begin(title, windowFlags)) {
            Window.block()
            IG.end()
        }
    }
}
object Window {
    inline fun menuBar(block: MenuBar.() -> Unit) {
        if(imgui.ImGui.beginMenuBar()) {
            MenuBar.block()
            imgui.ImGui.endMenuBar()
        }
    }

    fun text(text: String, onclick: () -> Unit = { }) {
        imgui.ImGui.text(text)
        if(imgui.ImGui.isItemClicked()) {
            onclick()
        }
    }
    fun checkBox(label: String, initial: Boolean, onChange: (Boolean) -> Unit = { }) {
        val booleanValue = ImBoolean(initial)
        if(imgui.ImGui.checkbox(label, booleanValue)) {
            val currentValue = booleanValue.get()
            if(initial != currentValue) {
                onChange(currentValue)
            }
        }
    }

    inline fun treeNode(label: String, block: TreeNode.() -> Unit) = treeNodeImpl(label, block)
}

@PublishedApi
internal inline fun treeNodeImpl(label: String, block: TreeNode.() -> Unit) {
    if(imgui.ImGui.treeNode(label)) {
        TreeNode.block()
        imgui.ImGui.treePop()
    }
}
object TreeNode {
    inline fun text(text: String, onclick: () -> Unit = { }) {
        imgui.ImGui.indent()
        imgui.ImGui.text(text)
        if(imgui.ImGui.isItemClicked()) {
            onclick()
        }
        imgui.ImGui.unindent()
    }

    inline fun treeNode(label: String, block: TreeNode.() -> Unit) = treeNodeImpl(label, block)
}
object MenuBar {
    inline fun menu(label: String, block: Menu.() -> Unit) {
        if(IG.beginMenu(label)) {
            Menu.block()
            imgui.ImGui.endMenu()
        }
    }
}
object Menu {
    inline fun menuItem(label: String, onClick: () -> Unit) {
        if(IG.menuItem(label)) {
            onClick()
        }
    }
}