package de.hanno.hpengine.editor

import de.hanno.hpengine.editor.selection.SelectionListener
import de.hanno.hpengine.engine.Engine
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.joml.Vector4f
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.ArrayList
import java.util.HashMap
import java.util.logging.Logger
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

open class SceneTree(val engine: Engine<*>,
                val editorComponents: EditorComponents,
                val rootNode: DefaultMutableTreeNode = DefaultMutableTreeNode(engine.scene)) : JTree(rootNode) {

    private val editor: RibbonEditor = editorComponents.editor
    private var selectionListener: SelectionListener? = null

    init {
        addSceneObjects()
        engine.eventBus.register(this)
    }

    private fun DefaultMutableTreeNode.findChild(query: Any): DefaultMutableTreeNode? {
        if(userObject == query) return this

        return children().toList().filterIsInstance<DefaultMutableTreeNode>().firstNotNullResult { it.findChild(query) }
    }
    fun getSelectionPath(query: Any): TreePath? = rootNode.findChild(query)?.path?.let { TreePath(it) }

    fun select(any: Any) = SwingUtils.invokeLater {
        getSelectionPath(any).let {
            this@SceneTree.selectionPath = it
            this@SceneTree.scrollPathToVisible(it)
        }
    }

    fun reload() {
        addSceneObjects()
        val model = model as DefaultTreeModel

        SwingUtilities.invokeLater {
            model.reload()
            revalidate()
            repaint()
            LOGGER.info("Reload scene tree")
        }
    }

    private fun addSceneObjects(): DefaultMutableTreeNode {
        val top = rootNode
        top.removeAllChildren()

        spanTree(top, engine.sceneManager.scene.entityManager.getEntities())
        LOGGER.info("Added " + engine.sceneManager.scene.getEntities().size)

        if (selectionListener == null) {
            selectionListener = SelectionListener(this, editorComponents)
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
        if(parent.isRoot) {
            parent.add(DefaultMutableTreeNode(editorComponents.sphereHolder.sphereEntity))
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

    fun SceneTree.handleContextMenu(mouseEvent: MouseEvent, selection: Any) {
        if (mouseEvent.isPopupTrigger) {
            when (selection) {
                is Entity -> {
                    JPopupMenu().apply {
                        val menu = JMenu("Add").apply {
                            val modelComponentMenuItem = JMenuItem("ModelComponent").apply {
                                addActionListener {
                                    JFileChooser(engine.directories.gameDir).apply {
                                        if (showOpenDialog(editorComponents.editor) == JFileChooser.APPROVE_OPTION) {
                                            GlobalScope.launch {
                                                val loadedModels = LoadModelCommand(selectedFile,
                                                        "Model_${System.currentTimeMillis()}",
                                                        engine.scene.materialManager,
                                                        engine.directories.gameDir,
                                                        selection).execute()
                                                engine.addResourceContext.launch {
                                                    with(engine.scene) {
                                                        addAll(loadedModels.entities)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            val lightMenuItem = JMenu("Light").apply {
                                add(JMenuItem("PointLight").apply {
                                    addActionListener {
                                        GlobalScope.launch {
                                            val component = PointLight(selection, Vector4f(1f, 1f, 1f, 1f), 10f)
                                            engine.sceneManager.addComponent(selection, component)
                                        }
                                    }
                                })
                            }

                            add(modelComponentMenuItem)
                            add(lightMenuItem)
                        }
                        add(menu)
                        show(mouseEvent.component, mouseEvent.x, mouseEvent.y)
                    }
                }
            }
        }
    }

fun SceneTree.addDefaultMouseListener() {
    addMouseListener(object : MouseAdapter() {
        override fun mousePressed(mouseEvent: MouseEvent) {
            setSelectionRow(getClosestRowForLocation(mouseEvent.x, mouseEvent.y))
            val selectedTreeElement = (lastSelectedPathComponent as DefaultMutableTreeNode).userObject

            handleContextMenu(mouseEvent, selectedTreeElement)
        }
    })
}