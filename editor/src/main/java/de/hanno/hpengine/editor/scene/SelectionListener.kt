package de.hanno.hpengine.editor.scene

import de.hanno.hpengine.editor.selection.Selection
import de.hanno.hpengine.editor.selection.SelectionSystem
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode

class SelectionListener(
    private val tree: SceneTree,
    private val selectionSystem: SelectionSystem
) : MouseAdapter(), TreeSelectionListener {

    init {
        tree.addTreeSelectionListener(this)
        tree.addMouseListener(this)
    }
    override fun mousePressed(mouseEvent: MouseEvent) {
        tree.setSelectionRow(tree.getClosestRowForLocation(mouseEvent.x, mouseEvent.y))
        val selectedTreeElement = (tree.lastSelectedPathComponent as DefaultMutableTreeNode).userObject

        selectedTreeElement ?: return

        tree.handleContextMenu(mouseEvent, selectedTreeElement)

        if (selectedTreeElement !is Selection) return
        handleClick(mouseEvent, selectedTreeElement)
    }

//    override fun valueChanged(e: TreeSelectionEvent) {
//        val treeNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
//
//        if (e.oldLeadSelectionPath == e.newLeadSelectionPath) return
//
//        val paths = tree.selectionPaths
//        val currentPath = e.path
//
//        if (paths != null) {
//            for (treePath in paths) {
//                if (treePath === currentPath) {
//                    continue
//                }
//                tree.removeSelectionPath(treePath)
//            }
//        }
//
//        selectionSystem.selectOrUnselect(treeNode.userObject as Selection)
//    }

    private fun handleClick(mouseEvent: MouseEvent, selection: Selection) {
        if (mouseEvent.button == 1) {
            selectionSystem.selectOrUnselect(selection)
        }
    }

    override fun valueChanged(p0: TreeSelectionEvent?) { }
}