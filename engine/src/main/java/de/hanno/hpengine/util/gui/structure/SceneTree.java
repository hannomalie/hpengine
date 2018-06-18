package de.hanno.hpengine.util.gui.structure;

import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.extended.tree.WebCheckBoxTreeCellRenderer;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.util.Parentable;
import de.hanno.hpengine.util.gui.SetVisibilityCheckStateListener;
import de.hanno.hpengine.util.gui.DebugFrame;
import de.hanno.hpengine.util.gui.SetSelectedListener;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SceneTree extends WebCheckBoxTree {

    private static final Logger LOGGER = Logger.getLogger(SceneTree.class.getName());

    private SetSelectedListener selectionListener = null;
    private Engine engine;

    public SceneTree(Engine engine) {
        super(new DefaultMutableTreeNode("Scene"));
        this.engine = engine;
        addCheckStateChangeListener(new SetVisibilityCheckStateListener());
        addOctreeSceneObjects();
        engine.getEventBus().register(this);
    }

    public void reload() {
        addOctreeSceneObjects();
        DefaultTreeModel model = (DefaultTreeModel)getModel();
        model.reload();
        revalidate();
        repaint();
        LOGGER.info("Reloaded de.hanno.hpengine.scene tree");
    }

    private DefaultMutableTreeNode addOctreeSceneObjects() {
        DefaultMutableTreeNode top = getRootNode();
        top.removeAllChildren();

        if(engine.getSceneManager().getScene() != null) {
            spanTree(top, engine.getSceneManager().getScene().getEntityManager().getEntities());
            LOGGER.info("Added " + engine.getSceneManager().getScene().getEntities().size());
        } else {
            LOGGER.info("Scene is currently null");
        }
        if(selectionListener == null) {
            selectionListener = new SetSelectedListener(this, engine);
        }
        setCheckBoxTreeCellRenderer(new WebCheckBoxTreeCellRenderer(this) {
            private JLabel lblNull = new JLabel("");

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean arg2, boolean arg3, boolean arg4, int arg5, boolean arg6) {

                Component c = super.getTreeCellRendererComponent(tree, value, arg2, arg3, arg4, arg5, arg6);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Optional<Entity> entityOption = engine.getSceneManager().getScene().getEntity(node.toString());
                if(entityOption.isPresent()) {
                    if(entityOption.get().isVisible()) {
                        checkBox.setChecked();
                    }
                }
                if (matchesFilter(node)) {
                    c.setForeground(Color.BLACK);
                    c.setVisible(false);
                    return c;
                }
                else if (containsMatchingChild(node)) {
                    c.setForeground(Color.GRAY);
                    c.setVisible(false);
                    return c;
                }
                else {
                    c.setVisible(false);
                    return lblNull;
                }
            }

            private boolean matchesFilter(DefaultMutableTreeNode node) {
                String filterText = DebugFrame.getCurrentFilter();
                return "".equals(filterText) || (node.getUserObject().toString()).startsWith(filterText);
            }

            private boolean containsMatchingChild(DefaultMutableTreeNode node) {
                Enumeration<DefaultMutableTreeNode> e = node.breadthFirstEnumeration();
                while (e.hasMoreElements()) {
                    if (matchesFilter(e.nextElement())) {
                        return true;
                    }
                }

                return false;
            }
        });

        return top;
    }

    private void spanTree(DefaultMutableTreeNode parent, List<Entity> entities) {

        java.util.List<Entity> rootEntities = new ArrayList<>();
        rootEntities.addAll(entities);

        Map<Entity, DefaultMutableTreeNode> rootEntityMappings = new HashMap<>();

        for(Entity entity : rootEntities.stream().filter(e -> !e.hasParent()).collect(Collectors.toList())) {
            DefaultMutableTreeNode current = new DefaultMutableTreeNode(entity);
            rootEntityMappings.put(entity, current);
            parent.add(current);
        }

        for(Entity entity : rootEntities.stream().filter(Parentable::hasParent).collect(Collectors.toList())) {
            DefaultMutableTreeNode current = new DefaultMutableTreeNode(entity);
            rootEntityMappings.get(entity.getParent()).add(current);
        }
    }

}
