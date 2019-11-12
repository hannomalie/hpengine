package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.model.Mesh
import de.hanno.hpengine.engine.scene.EnvironmentProbe
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class SelectionListener(internal var tree: JTree, private val editor: RibbonEditor) : TreeSelectionListener {

    init {
        tree.addTreeSelectionListener(this)
        tree.selectionModel.selectionMode = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

    }

    override fun valueChanged(e: TreeSelectionEvent) {
        val treeNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return

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

        val node = treeNode.userObject

        // TODO: MIIIIEEEEEES
        if (node is EnvironmentProbe) {
        } else if (node is Entity) {
            if(node == editor.entitySelector.selection) {
                editor.entitySelector.unselect()
            } else {
                editor.entitySelector.selectEntity(node)
            }
        } else if (node is Mesh<*>) {
//            entityViewFrame.contentPane.removeAll()
//            entityViewFrame.add(MeshView(engine, node))
//            entityViewFrame.isVisible = true
        }
    }
}
