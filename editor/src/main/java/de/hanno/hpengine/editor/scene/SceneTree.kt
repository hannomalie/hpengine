package de.hanno.hpengine.editor.scene

import de.hanno.hpengine.editor.selection.MaterialSelection
import de.hanno.hpengine.editor.selection.MeshSelection
import de.hanno.hpengine.editor.selection.ModelComponentSelection
import de.hanno.hpengine.editor.selection.Selection
import de.hanno.hpengine.editor.selection.SelectionSystem
import de.hanno.hpengine.editor.selection.SimpleEntitySelection
import de.hanno.hpengine.editor.window.SwingUtils
import de.hanno.hpengine.engine.component.Component
import de.hanno.hpengine.engine.component.ModelComponent
import de.hanno.hpengine.engine.config.Config
import de.hanno.hpengine.engine.entity.Entity
import de.hanno.hpengine.engine.graphics.light.point.PointLight
import de.hanno.hpengine.engine.graphics.renderer.command.LoadModelCommand
import de.hanno.hpengine.engine.model.texture.TextureManager
import de.hanno.hpengine.engine.scene.AddResourceContext
import de.hanno.hpengine.engine.scene.SceneManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import org.joml.Vector4f
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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

class SceneTree(
    val config: Config,
    val textureManager: TextureManager,
    val addResourceContext: AddResourceContext,
    val sceneManager: SceneManager,
    val rootNode: DefaultMutableTreeNode = DefaultMutableTreeNode()
) : JTree(rootNode) {

    private var selectionListener: de.hanno.hpengine.editor.selection.SelectionListener? = null

    private fun DefaultMutableTreeNode.findChild(query: Any): DefaultMutableTreeNode? {
        if (userObject == query) return this

        return children().toList().filterIsInstance<DefaultMutableTreeNode>().firstNotNullResult { it.findChild(query) }
    }

    fun getSelectionPath(query: Any): TreePath? = rootNode.findChild(query)?.path?.let { TreePath(it) }

    fun select(any: Any) = SwingUtils.invokeLater {
        getSelectionPath(any).let {
            selectionPath = it
            scrollPathToVisible(it)
        }
    }

    fun selectParent(nextSelection: Selection) = SwingUtils.invokeLater {
        getSelectionPath(nextSelection)?.let {
            this@SceneTree.selectionPath = it.parentPath
            this@SceneTree.scrollPathToVisible(it.parentPath)
        }
    }

    fun unselect() = SwingUtils.invokeLater {
        selectionPath = null
    }

    fun reload(entities: List<Entity>) {
        addSceneObjects(entities)
        val model = model as DefaultTreeModel

        SwingUtilities.invokeLater {
            model.reload()
            revalidate()
            repaint()
            LOGGER.info("Reload scene tree")
        }
    }

    private fun addSceneObjects(entities: List<Entity>): DefaultMutableTreeNode {
        val top = rootNode
        top.removeAllChildren()

        spanTree(top, entities)
        LOGGER.info("Added " + entities.size)

//        TODO: Reimplmeent without dependency to editorComponents
//        if (selectionListener == null) {
//            selectionListener = SelectionListener(this, editorComponents)
//        }
        return top
    }

    private fun spanTree(parent: DefaultMutableTreeNode, entities: List<Entity>) {

        val rootEntities = ArrayList<Entity>()
        rootEntities.addAll(entities)

        val rootEntityMappings = HashMap<Entity, DefaultMutableTreeNode>()

        for (entity in rootEntities.filter { e -> !e.hasParent }) {
            val current = DefaultMutableTreeNode(SimpleEntitySelection(entity))
            entity.components.forEach { component -> addComponentNode(current, component) }
            rootEntityMappings[entity] = current
            parent.add(current)
        }

        for (entity in rootEntities.filter { it.hasParent }) {
            val current = DefaultMutableTreeNode(SimpleEntitySelection(entity))
            rootEntityMappings[entity.parent]!!.add(current)
        }
//        TODO: Reimplment without depending on editorComponents
//        if (parent.isRoot) {
//            parent.add(DefaultMutableTreeNode(editorComponents.sphereHolder.sphereEntity))
//        }
    }

    private fun addComponentNode(current: DefaultMutableTreeNode, component: Component) {
        if (component is ModelComponent) {
            val componentNode = DefaultMutableTreeNode(ModelComponentSelection(component.entity, component))
            for (mesh in component.meshes) {
                componentNode.add(
                    DefaultMutableTreeNode(MeshSelection(component.entity, mesh)).apply {
                        add(DefaultMutableTreeNode(MaterialSelection(mesh.material)))
                    }
                )
            }
            current.add(componentNode)
            current.add(DefaultMutableTreeNode(MaterialSelection(component.model.material)))
        } else {
            current.add(DefaultMutableTreeNode(component))
        }
    }

    inner class SelectionListener(private val selectionSystem: SelectionSystem) : MouseAdapter() {
        override fun mousePressed(mouseEvent: MouseEvent) {
            setSelectionRow(getClosestRowForLocation(mouseEvent.x, mouseEvent.y))
            val selectedTreeElement = (lastSelectedPathComponent as DefaultMutableTreeNode).userObject

            selectedTreeElement ?: return

            handleContextMenu(mouseEvent, selectedTreeElement)

            if (selectedTreeElement !is Selection) return
            handleClick(mouseEvent, selectedTreeElement)
        }

        private fun handleClick(mouseEvent: MouseEvent, selection: Selection) {
            if (mouseEvent.button == 1) {
                selectionSystem.selectOrUnselect(selection)
            }
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(SceneTree::class.java.name)
    }

}

fun SceneTree.handleContextMenu(mouseEvent: MouseEvent, selection: Any) {
    if (mouseEvent.isPopupTrigger || mouseEvent.button == 3) {
        when (selection) {
            is Entity -> {
                JPopupMenu().apply {
                    val menu = JMenu("Add").apply {
                        val modelComponentMenuItem = JMenuItem("ModelComponent").apply {
                            addActionListener {
                                JFileChooser(config.gameDir.baseDir).apply {
                                    if (showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                                        GlobalScope.launch {
                                            val baseDirPath =
                                                config.gameDir.baseDir.canonicalPath.toString()
                                            require(selectedFile.canonicalPath.startsWith(baseDirPath)) { "Can only load from within the game directory" }

                                            val resultingPath =
                                                selectedFile.canonicalPath.replace(baseDirPath, "").let {
                                                    if (it.startsWith("/")) it.replaceFirst("/", "") else it
                                                }.let {
                                                    if (it.startsWith("\\")) it.replaceFirst("\\", "") else it
                                                }
                                            val loadedModels = LoadModelCommand(
                                                resultingPath,
                                                "Model_${System.currentTimeMillis()}",
                                                textureManager,
                                                config.directories.gameDir,
                                                selection
                                            ).execute()

                                            addResourceContext.launch {
                                                sceneManager.scene.addAll(loadedModels.entities)
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
                                        val component = PointLight(selection, Vector4f(1f, 1f, 1f, 1f))
                                        sceneManager.scene.addComponent(selection, component)
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

fun SceneTree.addDefaultMouseListener(selectionSystem: SelectionSystem) {
    addMouseListener(SelectionListener(selectionSystem))
}
