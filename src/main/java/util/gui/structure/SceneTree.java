package util.gui.structure;

import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.extended.tree.WebCheckBoxTreeCellRenderer;
import container.EntitiesContainer;
import engine.AppContext;
import engine.model.Entity;
import util.gui.DebugFrame;
import util.gui.SetSelectedListener;
import util.gui.SetVisibilityCheckStateListener;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SceneTree extends WebCheckBoxTree {

    private static final Logger LOGGER = Logger.getLogger(SceneTree.class.getName());

    private SetSelectedListener selectionListener = null;

    public SceneTree() {
        super(new DefaultMutableTreeNode("Scene"));
        addOctreeSceneObjects();
        addCheckStateChangeListener(new SetVisibilityCheckStateListener());
        AppContext.getEventBus().register(this);
    }

    public void reload() {
        addOctreeSceneObjects();
        DefaultTreeModel model = (DefaultTreeModel)getModel();
        model.reload();
        revalidate();
        repaint();
        LOGGER.info("Reloaded scene tree");
    }

    private DefaultMutableTreeNode addOctreeSceneObjects() {
        AppContext appContext = AppContext.getInstance();
        DefaultMutableTreeNode top = getRootNode();
        top.removeAllChildren();

        if(appContext.getScene() != null) {
            spanTree(top, appContext.getScene().getEntitiesContainer());
            LOGGER.info("Added " + appContext.getScene().getEntities().size());
        } else {
            LOGGER.info("Scene is currently null");
        }
        if(selectionListener == null) {
            selectionListener = new SetSelectedListener(this, appContext);
        }
        setCheckBoxTreeCellRenderer(new WebCheckBoxTreeCellRenderer(this) {
            private JLabel lblNull = new JLabel("");

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean arg2, boolean arg3, boolean arg4, int arg5, boolean arg6) {

                Component c = super.getTreeCellRendererComponent(tree, value, arg2, arg3, arg4, arg5, arg6);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
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

    private void spanTree(DefaultMutableTreeNode parent, EntitiesContainer container) {

        java.util.List<Entity> rootEntities = new ArrayList<>();
        rootEntities.addAll(container.getEntities());

        Map<Entity, DefaultMutableTreeNode> rootEntityMappings = new HashMap<>();

        for(Entity entity : rootEntities.stream().filter(e -> !e.hasParent()).collect(Collectors.toList())) {
            DefaultMutableTreeNode current = new DefaultMutableTreeNode(entity);
            rootEntityMappings.put(entity, current);
            parent.add(current);
        }


        for(Entity entity : rootEntities.stream().filter(e -> e.hasParent()).collect(Collectors.toList())) {
            DefaultMutableTreeNode current = new DefaultMutableTreeNode(entity);
            rootEntityMappings.get(entity.getParent()).add(current);
        }
    }

}
