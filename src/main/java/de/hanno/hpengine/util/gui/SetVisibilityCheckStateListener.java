package de.hanno.hpengine.util.gui;

import com.alee.extended.checkbox.CheckState;
import com.alee.extended.tree.CheckStateChange;
import com.alee.extended.tree.CheckStateChangeListener;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.container.Octree;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

public class SetVisibilityCheckStateListener implements CheckStateChangeListener<DefaultMutableTreeNode> {
    @Override
    public void checkStateChanged(List<CheckStateChange<DefaultMutableTreeNode>> stateChanges) {
        for (CheckStateChange<DefaultMutableTreeNode> checkStateChange : stateChanges) {
            boolean checked = checkStateChange.getNewState() == CheckState.checked ? true : false;

            Object object = checkStateChange.getNode().getUserObject();
            if(object instanceof Entity) {
                Entity entity = (Entity) object;
                entity.setVisible(checked);
            } else if (object instanceof Octree.Node) {
                Octree.Node node = (Octree.Node) object;
                List<Entity> result = new ArrayList<>();
                node.getAllEntitiesInAndBelow(result);
                for (Entity e : result) {
                    e.setVisible(checked);
                }
            }
        }
    }
}