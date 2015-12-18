package util.gui.structure;

import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.extended.tree.WebCheckBoxTreeCellRenderer;
import engine.AppContext;
import engine.model.Entity;
import octree.Octree;
import util.gui.DebugFrame;
import util.gui.SetSelectedListener;
import util.gui.SetVisibilityCheckStateListener;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class SceneTree extends WebCheckBoxTree {

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
        System.out.println("Reloaded scene tree");
    }

    private DefaultMutableTreeNode addOctreeSceneObjects() {
        AppContext appContext = AppContext.getInstance();
        DefaultMutableTreeNode top = getRootNode();
        top.removeAllChildren();

        if(appContext.getScene() != null) {
            addOctreeChildren(top, appContext.getScene().getOctree().rootNode);
            System.out.println("Added " + appContext.getScene().getEntities().size());
        } else {
            System.out.println("Scene is currently null");
        }
        new SetSelectedListener(this, appContext);
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

    private void addOctreeChildren(DefaultMutableTreeNode parent, Octree.Node node) {

        java.util.List<Entity> entitiesInAndBelow = new ArrayList<Entity>();
        node.getAllEntitiesInAndBelow(entitiesInAndBelow);

        DefaultMutableTreeNode current = new DefaultMutableTreeNode(node.toString() + " (" + entitiesInAndBelow.size() + " Entities in/below)");
        parent.add(current);
        if(node.hasChildren()) {
            for(int i = 0; i < 8; i++) {
                addOctreeChildren(current, node.children[i]);
            }
        }

        for (Entity entity : node.getEntities()) {
            if(entity.hasParent()) { continue; }
            DefaultMutableTreeNode currentEntity = new DefaultMutableTreeNode(entity);
            if(entity.hasChildren()) {
                entity.getChildren().forEach(child -> {
                    currentEntity.add(new DefaultMutableTreeNode(child));
                });
            }
            current.add(currentEntity);
        }

        if (node.hasChildren() && node.getEntities().size() > 0 && !node.isRoot()) {
            System.out.println("FUUUUUUUUUUUUUUUUUUUUCK deepness is " + node.getDeepness());
        }
    }

}
