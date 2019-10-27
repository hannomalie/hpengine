package de.hanno.hpengine.editor

import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.util.gui.SetSelectedListener
import java.util.ArrayList
import java.util.HashMap
import java.util.logging.Logger
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SceneTree(private val engine: Engine<*>,
                private val editor: RibbonEditor,
                private val rootNode: DefaultMutableTreeNode = DefaultMutableTreeNode("Scene")) : JTree(rootNode) {

    private var selectionListener: SelectionListener? = null

    init {
        addSceneObjects()
        engine.eventBus.register(this)
    }

    fun reload() {
        addSceneObjects()
        val model = model as DefaultTreeModel
        model.reload()
        revalidate()
        repaint()
        LOGGER.info("Reload scene tree")
    }

    private fun addSceneObjects(): DefaultMutableTreeNode {
        val top = rootNode
        top.removeAllChildren()

        spanTree(top, engine.sceneManager.scene.entityManager.getEntities())
        LOGGER.info("Added " + engine.sceneManager.scene.getEntities().size)

        if (selectionListener == null) {
            selectionListener = SelectionListener(this, editor)
        }
        return top
    }

    private fun spanTree(parent: DefaultMutableTreeNode, entities: List<Entity>) {

        val rootEntities = ArrayList<Entity>()
        rootEntities.addAll(entities)

        val rootEntityMappings = HashMap<Entity, DefaultMutableTreeNode>()

        for (entity in rootEntities.filter { e -> !e.hasParent() }) {
            val current = DefaultMutableTreeNode(entity)
            entity.components.values.forEach { component -> addComponentNode(current, component) }
            rootEntityMappings[entity] = current
            parent.add(current)
        }

        for (entity in rootEntities.filter { it.hasParent() }) {
            val current = DefaultMutableTreeNode(entity)
            rootEntityMappings[entity.parent]!!.add(current)
        }
    }

    private fun addComponentNode(current: DefaultMutableTreeNode, component: Component) {
        val componentNode = DefaultMutableTreeNode(component)
        current.add(componentNode)
        if (component is ModelComponent) {
            for (mesh in component.meshes) {
                componentNode.add(DefaultMutableTreeNode(mesh))
            }
        }
    }

    companion object {

        private val LOGGER = Logger.getLogger(SceneTree::class.java.name)
    }

}
