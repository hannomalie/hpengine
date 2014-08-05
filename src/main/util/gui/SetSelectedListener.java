package main.util.gui;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.renderer.material.Material;
import main.scene.EnvironmentProbe;

import com.alee.laf.rootpane.WebFrame;

public class SetSelectedListener implements TreeSelectionListener {

	JTree tree;
	private WebFrame entityViewFrame;
	private World world;
	private DebugFrame debugFrame;

	private SetSelectedListener() { }
	
	public SetSelectedListener(JTree tree, World world, DebugFrame debugFrame, WebFrame entityViewFrame) {
		this.tree = tree;
		this.debugFrame = debugFrame;
		this.entityViewFrame = entityViewFrame;
		this.world = world;
		tree.addTreeSelectionListener(this);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
	}
	
	@Override
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        
        TreePath[] paths = tree.getSelectionPaths();
        TreePath currentPath =  e.getPath();
        
        if (paths != null) {
            for (TreePath treePath : paths) {
            	if(treePath == currentPath) { continue; }
    			tree.removeSelectionPath(treePath);
    		}	
        }

        if (node == null) return;
        Object nodeInfo = node.getUserObject();
        
        if (nodeInfo instanceof Entity) {
        	Entity selected = (Entity) nodeInfo;
	    	entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	    	entityViewFrame.getContentPane().removeAll();
	    	entityViewFrame.pack();
	    	entityViewFrame.setSize(600, 600);
	    	entityViewFrame.add(new EntitiyView(world, debugFrame, (Entity) selected));
	    	entityViewFrame.setVisible(true);
        	
        } else if (nodeInfo instanceof EnvironmentProbe) {
        	EnvironmentProbe selected = (EnvironmentProbe) nodeInfo;
	    	entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	    	entityViewFrame.getContentPane().removeAll();
	    	entityViewFrame.pack();
	    	entityViewFrame.setSize(600, 600);
	    	entityViewFrame.add(new ProbeView(world, debugFrame, selected));
	    	entityViewFrame.setVisible(true);
        }
    }
}
