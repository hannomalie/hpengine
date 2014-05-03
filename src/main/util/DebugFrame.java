package main.util;

import static main.util.Util.vectorToString;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SpinnerModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;

import main.DeferredRenderer;
import main.IEntity;
import main.Material;
import main.PointLight;
import main.World;

import org.newdawn.slick.opengl.Texture;

import com.alee.laf.button.WebToggleButton;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.spinner.WebSpinner;

public class DebugFrame {

	private JFrame mainFrame = new JFrame();
	private JScrollPane materialPane = new JScrollPane();
	private JScrollPane texturePane = new JScrollPane();
	private JScrollPane lightsPane = new JScrollPane();
	private JScrollPane scenePane = new JScrollPane();
	private JPanel buttonPanel = new JPanel(new FlowLayout());
	
	private WebToggleButton toggleParallax = new WebToggleButton("Parallax", World.useParallax);
	private WebToggleButton toggleSteepParallax = new WebToggleButton("Steep Parallax", World.useSteepParallax);
	private WebToggleButton toggleAmbientOcclusion = new WebToggleButton("Ambient Occlusion", World.useAmbientOcclusion);
	private WebToggleButton toggleFrustumCulling = new WebToggleButton("Frustum Culling", World.useFrustumCulling);
	private WebToggleButton toggleDebugFrame = new WebToggleButton("Debug Frame", World.DEBUGFRAME_ENABLED);
	private WebToggleButton toggleDrawLights = new WebToggleButton("Draw Lights", World.DRAWLIGHTS_ENABLED);
	WebSlider ambientOcclusionFalloff = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionRadiusSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionTotalStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );
	WebSlider ambientOcclusionStrengthSlider = new WebSlider ( WebSlider.HORIZONTAL );
	
	
	private JTree scene = new JTree();
	
	public DebugFrame(World world) {
		
		mainFrame.setLayout(new BorderLayout(5,5));

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

		materialPane =  new JScrollPane(materialTable);
		texturePane  =  new JScrollPane(textureTable);
		lightsPane  =  new JScrollPane(lightsTable);
		
		addSceneObjects(world);
		scenePane = new JScrollPane(scene);

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
	    ambientOcclusionRadiusSlider.setValue(100);
	    ambientOcclusionRadiusSlider.setPaintTicks ( true );
	    ambientOcclusionRadiusSlider.setPaintLabels ( true );
	    ambientOcclusionRadiusSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 100;
				World.AMBIENTOCCLUSION_RADIUS = World.AMBIENTOCCLUSION_FACTOR * valueAsFactor;
			}
		});

	    ambientOcclusionTotalStrengthSlider.setMinimum ( 0 );
	    ambientOcclusionTotalStrengthSlider.setMaximum ( 200 );
	    ambientOcclusionTotalStrengthSlider.setMinorTickSpacing ( 20 );
	    ambientOcclusionTotalStrengthSlider.setMajorTickSpacing ( 50 );
	    ambientOcclusionTotalStrengthSlider.setValue(38);
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

	    ambientOcclusionStrengthSlider.setMinimum ( 0 );
	    ambientOcclusionStrengthSlider.setMaximum ( 100 );
	    ambientOcclusionStrengthSlider.setMinorTickSpacing ( 10 );
	    ambientOcclusionStrengthSlider.setMajorTickSpacing ( 20 );
	    ambientOcclusionStrengthSlider.setValue(7);
	    ambientOcclusionStrengthSlider.setPaintTicks ( true );
	    ambientOcclusionStrengthSlider.setPaintLabels ( true );
	    ambientOcclusionStrengthSlider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 1000;
				World.AMBIENTOCCLUSION_STRENGTH = valueAsFactor;
			}
		});
	    
	    ambientOcclusionFalloff.setMinimum ( 0 );
	    ambientOcclusionFalloff.setMaximum ( 100 );
	    ambientOcclusionFalloff.setMinorTickSpacing ( 10 );
	    ambientOcclusionFalloff.setMajorTickSpacing ( 20 );
	    ambientOcclusionFalloff.setValue(7);
	    ambientOcclusionFalloff.setPaintTicks ( true );
	    ambientOcclusionFalloff.setPaintLabels ( true );
	    ambientOcclusionFalloff.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				WebSlider slider = (WebSlider) e.getSource();
				int value = slider.getValue();
				float valueAsFactor = ((float) value) / 10000000;
				World.AMBIENTOCCLUSION_FALLOFF = valueAsFactor;
			}
		});
		
		buttonPanel.add(toggleDebugFrame);
		buttonPanel.add(toggleDrawLights);
		buttonPanel.add(toggleParallax);
		buttonPanel.add(toggleSteepParallax);
		buttonPanel.add(toggleAmbientOcclusion);
		buttonPanel.add(toggleFrustumCulling);
		buttonPanel.add(ambientOcclusionRadiusSlider);
		buttonPanel.add(ambientOcclusionTotalStrengthSlider);
//		buttonPanel.add(ambientOcclusionStrengthSlider);
//		buttonPanel.add(ambientOcclusionFalloff);
		buttonPanel.setSize(200, 200);

//		mainFrame.add(materialPane);
		mainFrame.add(buttonPanel, BorderLayout.PAGE_START);
		mainFrame.add(texturePane, BorderLayout.LINE_START);
		mainFrame.add(lightsPane, BorderLayout.PAGE_END);
		mainFrame.add(scenePane, BorderLayout.CENTER);
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
