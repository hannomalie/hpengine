package main.util;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import main.IEntity;
import main.Material;
import main.World;

import org.newdawn.slick.opengl.Texture;

public class DebugFrame {

	private JFrame mainFrame = new JFrame();
	private JScrollPane materialPane = new JScrollPane();
	private JScrollPane texturePane = new JScrollPane();
	private JScrollPane scenePane = new JScrollPane();
	
	private JTree scene = new JTree();
	
	public DebugFrame(World world) {
		
		mainFrame.setLayout(new FlowLayout());

		TableModel materialDataModel = new AbstractTableModel() {

			List<Object> paths = Arrays.asList(Material.LIBRARY.keySet()
					.toArray());
			List<Object> materials = Arrays.asList(Material.LIBRARY.values()
					.toArray());

			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return Material.LIBRARY.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					return paths.get(row);
				}
				return materials.get(row);
			}
			
			public String getColumnName(int column) {
				if (column == 0) {
					return "Path";
				} else if (column == 1) {
					return "Material";
				}
				return "Null";
			}
		};
		
		TableModel textureDataModel = new AbstractTableModel() {

			List<Object> paths = Arrays.asList(Material.TEXTURES.keySet()
					.toArray());
			List<Object> textures = Arrays.asList(Material.TEXTURES.values()
					.toArray());

			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return Material.TEXTURES.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					return paths.get(row);
				}
				Texture texture = (Texture) textures.get(row);
				return String.format("Texture %d x %d", texture.getTextureWidth(), texture.getTextureHeight());
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Path";
				} else if (column == 1) {
					return "Texture";
				}
				return "Null";
			}
		};

		JTable materialTable = new JTable(materialDataModel);
		JTable textureTable = new JTable(textureDataModel);

		materialPane =  new JScrollPane(materialTable);
		texturePane  =  new JScrollPane(textureTable);
		
		addSceneObjects(world);
		scenePane = new JScrollPane(scene);
		
//		mainFrame.add(materialPane);
		mainFrame.add(texturePane);
		mainFrame.add(scenePane);
		
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setSize(new Dimension(1200, 300));
		mainFrame.setVisible(true);
	}

	private void addSceneObjects(World world) {
		DefaultMutableTreeNode top =
		        new DefaultMutableTreeNode("Scene");
		
		for (IEntity e : world.entities) {
			DefaultMutableTreeNode entityNode = new DefaultMutableTreeNode(e.getName());
			
			Material material = e.getMaterial();
			if (material != null) {
				DefaultMutableTreeNode materialNode = new DefaultMutableTreeNode(material.getName());
				
				for (Object map: Arrays.asList(material.textures.keySet().toArray())) {
					DefaultMutableTreeNode textureNode = new DefaultMutableTreeNode(String.format("%S - %s", map, material.textures.get(map)));
					materialNode.add(textureNode);
				}
				
				entityNode.add(materialNode);
			}
			
			top.add(entityNode);
		}
		
		scene = new JTree(top);
	}
	
	private class ImagePanel extends JPanel{

	    private BufferedImage image;

	    public ImagePanel(BufferedImage image) {
	       this.image = image;
	    }

	    @Override
	    protected void paintComponent(Graphics g) {
	        super.paintComponent(g);
	        g.drawImage(image, 0, 0, null); // see javadoc for more info on the parameters            
	    }

	}

}
