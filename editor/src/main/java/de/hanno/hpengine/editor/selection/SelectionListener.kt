package de.hanno.hpengine.editor.selection

import de.hanno.hpengine.editor.EditorComponents
import de.hanno.hpengine.engine.camera.Camera
import de.hanno.hpengine.engine.component.GIVolumeComponent
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.directional.DirectionalLight
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.extensions.ReflectionProbe
import de.hanno.hpengine.engine.scene.OceanWaterExtension
import de.hanno.hpengine.engine.scene.Scene
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class SelectionListener(internal var tree: JTree,
                        val editorComponents: EditorComponents) : TreeSelectionListener {

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

        editorComponents.selectionSystem.selectOrUnselect(treeNode.userObject as Selection)
    }
}
