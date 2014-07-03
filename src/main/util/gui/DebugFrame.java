package main.util.gui;

import static main.util.Util.vectorToString;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.MenuBar;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import main.World;
import main.model.IEntity;
import main.octree.Octree;
import main.octree.Octree.Node;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.PointLight;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;
import main.texture.TextureFactory;
import main.util.gui.input.Vector3fInput;
import main.util.script.ScriptManager;

import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.window.ComponentMoveAdapter;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextArea;


public class DebugFrame {

	ScriptManager scriptManager;
	private WebFrame mainFrame = new WebFrame("Main");
	private WebFrame materialViewFrame = new WebFrame("Material");
	private WebFrame entityViewFrame = new WebFrame("Entity");
	private WebTabbedPane tabbedPane = new WebTabbedPane();
	
	private JScrollPane materialPane = new JScrollPane();
	private JScrollPane texturePane = new JScrollPane();
	private JScrollPane lightsPane = new JScrollPane();
	private JScrollPane scenePane = new JScrollPane();
	private WebPanel scriptPanel;
	private JPanel buttonPanel = new JPanel(new FlowLayout());

	private WebToggleButton toggleFileReload = new WebToggleButton("Hot Reload", World.RELOAD_ON_FILE_CHANGE);
	private WebToggleButton toggleParallax = new WebToggleButton("Parallax", World.useParallax);
	private WebToggleButton toggleSteepParallax = new WebToggleButton("Steep Parallax", World.useSteepParallax);
	private WebToggleButton toggleAmbientOcclusion = new WebToggleButton("Ambient Occlusion", World.useAmbientOcclusion);
	private WebToggleButton toggleFrustumCulling = new WebToggleButton("Frustum Culling", World.useFrustumCulling);
	private WebToggleButton toggleDrawLines = new WebToggleButton("Draw Lines", World.DRAWLINES_ENABLED);
	private WebToggleButton toggleDrawOctree = new WebToggleButton("Draw Octree", Octree.DRAW_LINES);
	private WebToggleButton toggleDebugFrame = new WebToggleButton("Debug Frame", World.DEBUGFRAME_ENABLED);
	private WebToggleButton toggleDrawLights = new WebToggleButton("Draw Lights", World.DRAWLIGHTS_ENABLED);

	WebSlider ambientOcclusionRadiusSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionTotalStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
	WebColorChooserPanel ambientLightColorChooserPanel = new WebColorChooserPanel();


	private JTree scene = new JTree();
	private JTree sceneOctree = new JTree();
	
	public DebugFrame(World world) {
		
		scriptManager = new ScriptManager(world);
		MaterialFactory materialFactory = world.getRenderer().getMaterialFactory();
		TextureFactory textureFactory = world.getRenderer().getTextureFactory();
		
		mainFrame.setLayout(new BorderLayout(5,5));
		
		TableModel materialDataModel = new AbstractTableModel() {

			List<Object> paths = Arrays.asList(materialFactory.MATERIALS.keySet().toArray());
			List<Object> materials = Arrays.asList(materialFactory.MATERIALS.values().toArray());

			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return world.getRenderer().getMaterialFactory().MATERIALS.size();
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

			List<Object> paths = Arrays.asList(textureFactory.TEXTURES.keySet()
					.toArray());
			List<Object> textures = Arrays.asList(textureFactory.TEXTURES.values()
					.toArray());

			public int getColumnCount() {
				return 2;
			}

			public int getRowCount() {
				return textureFactory.TEXTURES.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					return paths.get(row);
				}
				main.texture.Texture texture = (main.texture.Texture) textures.get(row);
				return String.format("Texture %d x %d", texture.getImageWidth(), texture.getImageHeight());
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
		
		TableModel lightsTableModel = new AbstractTableModel() {

			List<PointLight> lights = DeferredRenderer.pointLights;

			public int getColumnCount() {
				return 3;
			}

			public int getRowCount() {
				return lights.size();
			}

			public Object getValueAt(int row, int col) {
				if (col == 0) {
					PointLight light = lights.get(row);
					return String.format("%s (Range %f)", light.getName(), light.getScale().x);
					
				} else if (col == 1) {
					return vectorToString(lights.get(row).getPosition());
					
				} else if (col == 2) {
					return vectorToString(lights.get(row).getColor());
					
				}
				return "";
			}

			public String getColumnName(int column) {
				if (column == 0) {
					return "Name";
				} else if (column == 1) {
					return "Position";
				} else if (column == 2) {
					return "Color";
				}
				return "Null";
			}
		};

		JTable materialTable = new JTable(materialDataModel);
		JTable textureTable = new JTable(textureDataModel);
		JTable lightsTable = new JTable(lightsTableModel);
		
		materialTable.getSelectionModel().addListSelectionListener(new ListSelectionListener(){
	        public void valueChanged(ListSelectionEvent event) {
	        	java.awt.EventQueue.invokeLater(new Runnable() {
	        	    @Override
	        	    public void run() {
	    	            // do some actions here, for example
	    	            // print first column value from selected row
	    	        	materialViewFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	    	        	materialViewFrame.getContentPane().removeAll();
	    	        	materialViewFrame.pack();
	    	        	materialViewFrame.setSize(600, 600);
	    	        	materialViewFrame.add(new MaterialView(world, (Material) materialTable.getValueAt(materialTable.getSelectedRow(), 1)));
	    	            materialViewFrame.setVisible(true);
	        	    }
	        	});
	        }
	    });

		materialPane =  new JScrollPane(materialTable);
		texturePane  =  new JScrollPane(textureTable);
		lightsPane  =  new JScrollPane(lightsTable);

		addSceneObjects(world);
		addOctreeSceneObjects(world);
		scenePane = new JScrollPane(sceneOctree);
		
		scriptPanel = new WebPanel();
		scriptPanel.setMargin ( 10 );
		WebTextArea scriptArea = new WebTextArea(600,600);
		scriptPanel.add(scriptArea, BorderLayout.CENTER);
		WebButton runScriptButton = new WebButton("Run");
		runScriptButton.addActionListener(e -> {
			try {
				scriptManager.eval(scriptArea.getText());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		});
		scriptPanel.add(runScriptButton, BorderLayout.NORTH);

		toggleFileReload.addActionListener( e -> {
			World.RELOAD_ON_FILE_CHANGE = !World.RELOAD_ON_FILE_CHANGE;
			toggleParallax.setSelected(World.RELOAD_ON_FILE_CHANGE);
		});
		
		toggleParallax.addActionListener( e -> {
			World.useParallax = !World.useParallax;
			World.useSteepParallax = false;
			toggleParallax.setSelected(World.useParallax);
		});
		
		toggleSteepParallax.addActionListener(e -> {
			World.useSteepParallax = !World.useSteepParallax;
			World.useParallax = false;
			toggleSteepParallax.setSelected(World.useSteepParallax);
		});

		toggleAmbientOcclusion.addActionListener(e -> {
			World.useAmbientOcclusion = !World.useAmbientOcclusion;
			toggleAmbientOcclusion.setSelected(World.useAmbientOcclusion);
		});
		
		toggleFrustumCulling.addActionListener(e -> {
			World.useFrustumCulling = !World.useFrustumCulling;
			toggleFrustumCulling.setSelected(World.useFrustumCulling);
		});

		toggleDrawLines.addActionListener(e -> {
			World.DRAWLINES_ENABLED = !World.DRAWLINES_ENABLED;
		});
		
		toggleDrawOctree.addActionListener(e -> {
			Octree.DRAW_LINES = !Octree.DRAW_LINES;
		});

		toggleDebugFrame.addActionListener(e -> {
			World.DEBUGFRAME_ENABLED = !World.DEBUGFRAME_ENABLED;
		});

		toggleDrawLights.addActionListener(e -> {
			World.DRAWLIGHTS_ENABLED = !World.DRAWLIGHTS_ENABLED;
		});

	    ambientOcclusionRadiusSlider.setMinimum ( 0 );
	    ambientOcclusionRadiusSlider.setMaximum ( 1000 );
	    ambientOcclusionRadiusSlider.setMinorTickSpacing ( 250 );
	    ambientOcclusionRadiusSlider.setMajorTickSpacing ( 500 );
	    ambientOcclusionRadiusSlider.setValue((int) (World.AMBIENTOCCLUSION_RADIUS * 10000f));
	    ambientOcclusionRadiusSlider.setPaintTicks ( true );
	    ambientOcclusionRadiusSlider.setPaintLabels ( true );
	    ambientOcclusionRadiusSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 10000;
				World.AMBIENTOCCLUSION_RADIUS = valueAsFactor;
			}
		});

	    ambientOcclusionTotalStrengthSlider.setMinimum ( 0 );
	    ambientOcclusionTotalStrengthSlider.setMaximum ( 200 );
	    ambientOcclusionTotalStrengthSlider.setMinorTickSpacing ( 20 );
	    ambientOcclusionTotalStrengthSlider.setMajorTickSpacing ( 50 );
	    ambientOcclusionTotalStrengthSlider.setValue((int) (World.AMBIENTOCCLUSION_TOTAL_STRENGTH * 100f));
	    ambientOcclusionTotalStrengthSlider.setPaintTicks ( true );
	    ambientOcclusionTotalStrengthSlider.setPaintLabels ( true );
	    ambientOcclusionTotalStrengthSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 100;
				World.AMBIENTOCCLUSION_TOTAL_STRENGTH = valueAsFactor;
			}
		});

		buttonPanel.add(toggleDrawLines);
		buttonPanel.add(toggleDrawOctree);
		buttonPanel.add(toggleDebugFrame);
		buttonPanel.add(toggleDrawLights);
		buttonPanel.add(new WebLabel("Direcitonal Light Dir:"));
		new Vector3fInput(buttonPanel) {
			
			@Override
			public void onChange(Vector3f value) {
				World.light.rotate(value, 0.7f);
			}
		};

		lightColorChooserPanel.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = lightColorChooserPanel.getColor();
				World.light.setColor(new Vector3f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f));
			}
		});
		buttonPanel.add(lightColorChooserPanel);
		
		ambientLightColorChooserPanel.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = ambientLightColorChooserPanel.getColor();
				World.AMBIENT_LIGHT = (new Vector3f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f));
			}
		});
		buttonPanel.add(ambientLightColorChooserPanel);

		buttonPanel.add(toggleFileReload);
		buttonPanel.add(toggleParallax);
		buttonPanel.add(toggleSteepParallax);
		buttonPanel.add(toggleAmbientOcclusion);
		buttonPanel.add(toggleFrustumCulling);
		buttonPanel.add(ambientOcclusionRadiusSlider);
		buttonPanel.add(ambientOcclusionTotalStrengthSlider);
		buttonPanel.setSize(200, 200);

//		mainFrame.add(materialPane);
		mainFrame.add(tabbedPane);
		
		tabbedPane.addTab("Main", buttonPanel);
		tabbedPane.addTab("Scene", scenePane);
		tabbedPane.addTab("Texture", texturePane);
		tabbedPane.addTab("Material", materialPane);
		tabbedPane.addTab("Light", lightsPane);
		tabbedPane.addTab("Script", scriptPanel);
		
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setSize(new Dimension(1200, 720));
		mainFrame.setVisible(true);
	}

	private void addSceneObjects(World world) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Scene");
		
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
		new SetSelectedListener(scene, world, entityViewFrame);
	}
	
	private void addOctreeSceneObjects(World world) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Scene (" + world.octree.getEntityCount() + " entities)");
		
		addOctreeChildren(top, world.octree.rootNode);
		sceneOctree = new JTree(top);
		new SetSelectedListener(sceneOctree, world, entityViewFrame);
	}
	
	private void addOctreeChildren(DefaultMutableTreeNode parent, Node node) {
		List<IEntity> entitiesInAndBelow = new ArrayList<IEntity>();
		node.getAllEntitiesInAndBelow(entitiesInAndBelow);
		
		DefaultMutableTreeNode current = new DefaultMutableTreeNode(node.toString() + " (" + entitiesInAndBelow.size() + " Entities in/below)");
		parent.add(current);
		if(node.hasChildren()) {
			for(int i = 0; i < 8; i++) {
				addOctreeChildren(current, node.children[i]);	
			}
		}
		
		for (IEntity entitiy : node.entities) {
			current.add(new DefaultMutableTreeNode(entitiy));
		}
		
		if (node.hasChildren() && node.entities.size() > 0) {
			System.out.println("FUUUUUUUUUUUUUUUUUUUUCK deepness is " + node.getDeepness());
		}
	}

}
