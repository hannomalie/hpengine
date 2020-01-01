package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.editor.RibbonEditor
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.scene.EnvironmentProbe
import de.hanno.hpengine.engine.scene.Scene
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class SelectionListener(internal var tree: JTree,
                        val editorComponents: EditorComponents) : TreeSelectionListener {

    private val editor: RibbonEditor = editorComponents.editor

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

        if (node is EnvironmentProbe) {
        } else if (node is Entity) {
            unselectOr(node) { editorComponents.selectionSystem.selectEntity(it) }
        } else if (node is MeshSelection) {
            unselectOr(node) { editorComponents.selectionSystem.selectMesh(node) }
        }  else if (node is ModelComponent) {
            unselectOr(node) { editorComponents.selectionSystem.selectModel(ModelSelection(node.entity, node.model)) }
        } else if (node is PointLight) {
            unselectOr(node) { editorComponents.selectionSystem.selectPointLight(node) }
        } else if (node is DirectionalLight) {
            unselectOr(node) { editorComponents.selectionSystem.selectDirectionalLight(node) }
        } else if (node is Camera) {
            unselectOr(node) { editorComponents.selectionSystem.selectCamera(node) }
        } else if (node is Scene) {
            unselectOr(node) { editorComponents.selectionSystem.selectScene(node)}
        }
    }

    private fun <T> unselectOr(node: T, block: (T) -> Unit) {
        if (node == editorComponents.selectionSystem.selection) {
            editorComponents.selectionSystem.unselect()
        } else {
            block(node)
        }
    }
}
