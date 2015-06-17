package util.gui;

import com.alee.laf.rootpane.WebFrame;
import engine.World;
import engine.model.Entity;
import scene.EnvironmentProbe;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

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
		
//		final SetSelectedListener myself = this;
//		tree.addMouseListener(new MouseAdapter() {
//			public void mousePressed(MouseEvent e) {
//		         int selRow = tree.getRowForLocation(e.getX(), e.getY());
//		         TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
//		         
//		         if(selRow != -1) {
//		             if(e.getClickCount() == 1) {
//		                 System.out.println("Single " + e.getButton());
//		             }
//		             else if(e.getClickCount() == 2) {
//		                 System.out.println("Double " + e.getButton());
//		             }
//		             if(e.getButton() == MouseEvent.BUTTON1) {
//		            	 
//		             }
//		         }
//		     }
//		});
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
        
        // MIIIIEEEEEES
        if (nodeInfo instanceof Entity) {
        	Entity selected = (Entity) nodeInfo;
	    	entityViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	    	entityViewFrame.getContentPane().removeAll();
	    	entityViewFrame.pack();
	    	entityViewFrame.setSize(600, 600);
	    	entityViewFrame.add(new EntityView(world, debugFrame, (Entity) selected));
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
