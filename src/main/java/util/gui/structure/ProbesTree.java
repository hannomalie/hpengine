package util.gui.structure;

import com.alee.extended.tree.WebCheckBoxTree;
import engine.AppContext;
import scene.EnvironmentProbe;
import scene.EnvironmentProbeFactory;
import util.gui.SetSelectedListener;
import util.gui.SetVisibilityCheckStateListener;

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
        addProbes(AppContext.getInstance());
        DefaultTreeModel model = (DefaultTreeModel)getModel();
        model.reload();
        revalidate();
        repaint();
        LOGGER.info("Reloaded scene tree");
    }


    private void addProbes(AppContext appContext) {

        DefaultMutableTreeNode top = getRootNode();
        top.removeAllChildren();

        List<EnvironmentProbe> probes = EnvironmentProbeFactory.getInstance().getProbes();
        for (EnvironmentProbe environmentProbe : probes) {
            top.add(new DefaultMutableTreeNode(environmentProbe));
        }

        if(selectionListener == null) {
            selectionListener = new SetSelectedListener(this, appContext);
            addCheckStateChangeListener(new SetVisibilityCheckStateListener());
        }
    }
}
