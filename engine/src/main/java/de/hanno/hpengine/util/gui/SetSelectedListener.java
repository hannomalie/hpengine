package de.hanno.hpengine.util.gui;

import com.alee.laf.rootpane.WebFrame;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class SetSelectedListener implements TreeSelectionListener {

	JTree tree;
	private final static WebFrame entityViewFrame = new WebFrame();
    static {
		entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		entityViewFrame.getContentPane().removeAll();
		entityViewFrame.pack();
		entityViewFrame.setSize(600, 600);
        entityViewFrame.setVisible(false);
    }
	private Engine engine;

	public SetSelectedListener(JTree tree, Engine engine) {
		this.tree = tree;
		this.engine = engine;

		tree.addTreeSelectionListener(this);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		
	}
	
	@Override
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        
        TreePath[] paths = tree.getSelectionPaths();
        TreePath currentPath =  e.getPath();
        
        if (paths != null) {
            for (TreePath treePath : paths) {
            	if(treePath == currentPath) { continue; }
    			tree.removeSelectionPath(treePath);
    		}	
        }

        if (treeNode == null) return;
        Object node = treeNode.getUserObject();

		// TODO: MIIIIEEEEEES
		if (node instanceof EnvironmentProbe) {
			EnvironmentProbe selected = (EnvironmentProbe) node;
			entityViewFrame.getContentPane().removeAll();
			entityViewFrame.add(new ProbeView(engine, selected));
			entityViewFrame.setVisible(true);
		} else if (node instanceof Entity) {
			Entity selected = (Entity) node;
			entityViewFrame.getContentPane().removeAll();
			entityViewFrame.add(new EntityView(engine, selected));
			entityViewFrame.setVisible(true);
		} else if (node instanceof Mesh) {
			Mesh selected = (Mesh) node;
			entityViewFrame.getContentPane().removeAll();
			entityViewFrame.add(new MeshView(engine, selected));
			entityViewFrame.setVisible(true);
		}
    }
}
