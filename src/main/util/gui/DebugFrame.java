package main.util.gui;

import static main.util.Util.vectorToString;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
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
import main.renderer.light.PointLight;
import main.renderer.material.Material;
import main.renderer.material.MaterialFactory;
import main.scene.Scene;
import main.texture.TextureFactory;
import main.util.gui.input.Vector3fInput;
import main.util.script.ScriptManager;

import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.checkbox.CheckState;
import com.alee.extended.tree.CheckStateChange;
import com.alee.extended.tree.CheckStateChangeListener;
import com.alee.extended.tree.TreeCheckingModel;
import com.alee.extended.tree.WebCheckBoxTree;
import com.alee.laf.button.WebButton;
import com.alee.laf.button.WebToggleButton;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.menu.WebMenu;
import com.alee.laf.menu.WebMenuBar;
import com.alee.laf.menu.WebMenuItem;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebTextArea;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.swing.StateProvider;


public class DebugFrame {

	ScriptManager scriptManager;
	private WebFrame mainFrame = new WebFrame("Main");
	private WebFrame materialViewFrame = new WebFrame("Material");
	private WebFrame entityViewFrame = new WebFrame("Entity");
	private WebTabbedPane tabbedPane;
	
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


	private WebCheckBoxTree<DefaultMutableTreeNode> scene = new WebCheckBoxTree<DefaultMutableTreeNode>();
	private WebFileChooser fileChooser;
	private WebFrame addEntityFrame;
	private World world;
	
	public DebugFrame(World world) {
		init(world);
	}

	private void init(World world) {
		this.world = world;
		tabbedPane = new WebTabbedPane();
		fileChooser = new WebFileChooser(new File("."));
		
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

//		addSceneObjects(world);
		addOctreeSceneObjects(world);
		
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

		WebMenuBar menuBar = new WebMenuBar ();
		WebMenu menuScene = new WebMenu("Scene");
        menuBar.setUndecorated ( true );
        {
	        WebMenuItem sceneSaveMenuItem = new WebMenuItem ( "Save" );
	        sceneSaveMenuItem.addActionListener(e -> {
	        	Object selection = WebOptionPane.showInputDialog( mainFrame, "Save scene as", "Save scene", WebOptionPane.QUESTION_MESSAGE, null, null, "default" );
	        	if(selection != null) {
	        		world.getScene().write(selection.toString());
	        		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
	                notificationPopup.setIcon(NotificationIcon.clock);
	                notificationPopup.setDisplayTime( 2000 );
	                notificationPopup.setContent(new WebLabel("Saved scene as " + selection));
	                NotificationManager.showNotification(notificationPopup);
	        	}
	        });

	        menuScene.add(sceneSaveMenuItem);
        }
        {
        	WebMenuItem sceneLoadMenuItem = new WebMenuItem ( "Load" );
        	sceneLoadMenuItem.addActionListener(e -> {
        		
	    		File chosenFile = fileChooser.showOpenDialog();
	    		if(chosenFile != null) {
	    			String sceneName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
	    			Scene newScene = Scene.read(world.getRenderer(), sceneName);
	    			world.setScene(newScene);
	    			init(world);
	    			
	    		}
	    		
        	});

	        menuScene.add(sceneLoadMenuItem);
        }
		WebMenu menuEntity = new WebMenu("Entity");
        {
        	WebMenuItem entitiyAddManuItem = new WebMenuItem ( "Add" );
        	entitiyAddManuItem.addActionListener(e -> {
        		
	    		addEntityFrame = new WebFrame("Add Entity");
	    		addEntityFrame.setSize(600, 600);
	    		addEntityFrame.add(new AddEntitiyView(world, this));
	    		addEntityFrame.setVisible(true);
	    		
        	});

        	menuEntity.add(entitiyAddManuItem);
        }

        menuBar.add(menuScene);
        menuBar.add(menuEntity);
        mainFrame.setJMenuBar(menuBar);
        
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
		
		for (IEntity e : world.getScene().getEntities()) {
			DefaultMutableTreeNode entityNode = new DefaultMutableTreeNode(e.getName());
			
			Material material = e.getMaterial();
			if (material != null) {
				DefaultMutableTreeNode materialNode = new DefaultMutableTreeNode(material.getName());
				
				for (Object map: Arrays.asList(material.getMaterialInfo().maps.getTextures().keySet().toArray())) {
					DefaultMutableTreeNode textureNode = new DefaultMutableTreeNode(String.format("%S - %s", map, material.getMaterialInfo().maps.get(map)));
					materialNode.add(textureNode);
				}
				
				entityNode.add(materialNode);
			}
			
			top.add(entityNode);
		}
		
		scene = new WebCheckBoxTree<DefaultMutableTreeNode>(top);
		addCheckStateListener(scene);
		new SetSelectedListener(scene, world, entityViewFrame);
	}

	private void addCheckStateListener(WebCheckBoxTree<DefaultMutableTreeNode> scene) {
		scene.addCheckStateChangeListener(new CheckStateChangeListener<DefaultMutableTreeNode>() {
			
			@Override
			public void checkStateChanged(List<CheckStateChange<DefaultMutableTreeNode>> stateChanges) {
				for (CheckStateChange<DefaultMutableTreeNode> checkStateChange : stateChanges) {
					boolean checked = checkStateChange.getNewState() == CheckState.checked ? true : false;
					
					Object object = checkStateChange.getNode().getUserObject();
					if(object instanceof IEntity) {
						IEntity entity = (IEntity) object;
						entity.setVisible(checked);
					} else if (object instanceof Node) {
						Node node = (Node) object;
						List<IEntity> result = new ArrayList<>();
						node.getAllEntitiesInAndBelow(result);
						for (IEntity e : result) {
							e.setVisible(checked);
						}
					}
				}
			}
		});
	}
	
	private void addOctreeSceneObjects(World world) {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("Scene (" + world.getScene().getEntities().size() + " entities)");
		
		addOctreeChildren(top, world.getScene().getOctree().rootNode);
		System.out.println("Added " + world.getScene().getEntities().size());
		scene = new WebCheckBoxTree<DefaultMutableTreeNode>(top);
		addCheckStateListener(scene);
		new SetSelectedListener(scene, world, entityViewFrame);

		tabbedPane.remove(scenePane);
		scenePane = new JScrollPane(scene);
		tabbedPane.addTab("Scene", scenePane);
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

	public void refreshSceneTree() {
		System.out.println("Refreshing");
//		tabbedPane.remove(scenePane);
		addOctreeSceneObjects(world);
//		tabbedPane.addTab("Scene", scenePane);
	}

}
