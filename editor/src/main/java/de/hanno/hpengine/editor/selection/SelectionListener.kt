package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.graphics.EditorRendersystem
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class SelectionListener(internal var tree: JTree,
                        val editorRendersystem: EditorRendersystem
) : TreeSelectionListener {

    init {
        tree.addTreeSelectionListener(this)
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

    }

    override fun valueChanged(e: TreeSelectionEvent) {
        val treeNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return

        if (e.oldLeadSelectionPath == e.newLeadSelectionPath) return

        val paths = tree.selectionPaths
        val currentPath = e.path

        if (paths != null) {
            for (treePath in paths) {
                if (treePath === currentPath) {
                    continue
                }
                tree.removeSelectionPath(treePath)
            }
        }

        editorRendersystem.selectionSystem.selectOrUnselect(treeNode.userObject as Selection)
    }
}
