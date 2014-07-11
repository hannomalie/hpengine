package main.util.gui;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import main.World;
import main.model.IEntity;
import main.renderer.material.Material;

import com.alee.laf.rootpane.WebFrame;

public class SetSelectedListener implements TreeSelectionListener {

	JTree tree;
	private WebFrame entityViewFrame;
	private World world;

	private SetSelectedListener() { }
	
	public SetSelectedListener(JTree tree, World world, WebFrame entityViewFrame) {
		this.tree = tree;
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
        
        if (nodeInfo instanceof IEntity) {
        	IEntity selected = (IEntity) nodeInfo;
//        	selected.setSelected(!selected.isSelected());
        	
        	java.awt.EventQueue.invokeLater(new Runnable() {
        	    @Override
        	    public void run() {
    	            // do some actions here, for example
    	            // print first column value from selected row
        	    	entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        	    	entityViewFrame.getContentPane().removeAll();
        	    	entityViewFrame.pack();
        	    	entityViewFrame.setSize(600, 600);
        	    	entityViewFrame.add(new EntitiyView(world, selected));
        	    	entityViewFrame.setVisible(true);
        	    }
        	});
        	
        }
    }
}
