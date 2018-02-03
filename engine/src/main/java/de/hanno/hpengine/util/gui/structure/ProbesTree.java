package de.hanno.hpengine.util.gui.structure;

import com.alee.extended.tree.WebCheckBoxTree;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.util.gui.SetVisibilityCheckStateListener;
import de.hanno.hpengine.util.gui.SetSelectedListener;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.logging.Logger;

public class ProbesTree extends WebCheckBoxTree {

    private static final Logger LOGGER = Logger.getLogger(ProbesTree.class.getName());
    private SetSelectedListener selectionListener;

    public ProbesTree() {
        super(new DefaultMutableTreeNode("Probes"));
    }

    public void reload() {
        addProbes(Engine.getInstance());
        DefaultTreeModel model = (DefaultTreeModel)getModel();
        model.reload();
        revalidate();
        repaint();
        LOGGER.info("Reloaded scene tree");
    }


    private void addProbes(Engine engine) {

        DefaultMutableTreeNode top = getRootNode();
        top.removeAllChildren();

        List<EnvironmentProbe> probes = Engine.getInstance().getEnvironmentProbeFactory().getProbes();
        for (EnvironmentProbe environmentProbe : probes) {
            top.add(new DefaultMutableTreeNode(environmentProbe));
        }

        if(selectionListener == null) {
            selectionListener = new SetSelectedListener(this, engine);
            addCheckStateChangeListener(new SetVisibilityCheckStateListener());
        }
    }
}
