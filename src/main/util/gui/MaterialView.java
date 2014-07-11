package main.util.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;

import main.World;
import main.renderer.command.InitMaterialCommand;
import main.renderer.command.InitMaterialCommand.MaterialResult;
import main.renderer.command.LoadModelCommand;
import main.renderer.command.LoadModelCommand.EntityListResult;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.scene.Scene;
import main.shader.Program;
import main.texture.Texture;
import main.util.gui.input.ColorChooserButton;
import main.util.gui.input.ColorChooserFrame;
import main.util.gui.input.LimitedWebFormattedTextField;
import main.util.gui.input.WebFormattedVec3Field;

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.BorderPanel;
import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.text.WebTextField;
import com.alee.managers.language.data.TooltipWay;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.managers.tooltip.TooltipManager;

public class MaterialView extends WebPanel {

	private Material material;
	private World world;
	private DebugFrame parent;

	public MaterialView(DebugFrame parent, World world, Material material) {
		this.parent = parent;
		this.world = world;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		init(material);
	}

	private void init(Material material) {
		this.material = material;
		this.removeAll();
		List<Component> panels = new ArrayList<>();
		
		addTexturePanel(panels);
        addValuePanels(panels);
        addShaderChoosers(panels);

        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(e -> {
        	Material.write(material, material.getMaterialInfo().name);
        });
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);

        WebScrollPane materialAttributesPane = new WebScrollPane(new GridPanel ( panels.size(), 1, components));
        this.setLayout(new BorderLayout());
        this.add(materialAttributesPane, BorderLayout.CENTER);
        this.add(saveButton, BorderLayout.SOUTH);
	}

	public MaterialView(World world, Material material) {
		this(null, world, material);
	}

	private void addTexturePanel(List<Component> panels) {
		
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        addExistingTexturesPanels(webComponentPanel);
        addMissingTexturesPanels(webComponentPanel);
        
        panels.add(webComponentPanel);
	}

	private void addMissingTexturesPanels(WebComponentPanel webComponentPanel) {
		EnumSet<MAP> missingMaps = EnumSet.allOf(MAP.class);
		missingMaps.removeAll(material.getMaterialInfo().maps.getTextures().keySet());
		for (MAP map : missingMaps) {
			WebLabel label = new WebLabel ( map.name() );
	        
	        Texture[] textures = new Texture[world.getRenderer().getTextureFactory().TEXTURES.values().size()];
	        world.getRenderer().getTextureFactory().TEXTURES.values().toArray(textures);
	        WebComboBox select = new WebComboBox(textures);
	        select.setSelectedIndex(-1);
	        
	        List allTexturesList = new ArrayList(world.getRenderer().getTextureFactory().TEXTURES.values());
	        
	        select.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Texture selectedTexture = textures[cb.getSelectedIndex()];
	        	material.getMaterialInfo().maps.put(map, selectedTexture);
	        	addMaterialInitCommand();
	        });
	        
	        WebButton removeTextureButton = new WebButton("Remove");
	        removeTextureButton.addActionListener(e -> {
	        	material.getMaterialInfo().maps.getTextures().remove(map);
		        select.setSelectedIndex(-1);
	        	addMaterialInitCommand();
	        });

	        GroupPanel groupPanel = new GroupPanel ( 4, label, select, removeTextureButton );
			webComponentPanel.addElement(groupPanel);
		}
	}
	
	private void addExistingTexturesPanels(WebComponentPanel webComponentPanel) {
		for (MAP map : material.getMaterialInfo().maps.getTextures().keySet()) {
			Texture texture = material.getMaterialInfo().maps.getTextures().get(map);
			
	        WebLabel label = new WebLabel ( map.name() );
	        
	        Texture[] textures = new Texture[world.getRenderer().getTextureFactory().TEXTURES.values().size()];
	        world.getRenderer().getTextureFactory().TEXTURES.values().toArray(textures);
	        WebComboBox select = new WebComboBox(textures);
	        
	        List allTexturesList = new ArrayList(world.getRenderer().getTextureFactory().TEXTURES.values());
	        int assignedTexture = allTexturesList.indexOf(world.getRenderer().getTextureFactory().TEXTURES.get(material.getMaterialInfo().maps.getTextures().get(map).getPath()));
	        select.setSelectedIndex(assignedTexture);
	        
	        select.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Texture selectedTexture = textures[cb.getSelectedIndex()];
	        	material.getMaterialInfo().maps.put(map, selectedTexture);
	        });
	        
	        WebButton removeTextureButton = new WebButton("Remove");
	        removeTextureButton.addActionListener(e -> {
	        	material.getMaterialInfo().maps.getTextures().remove(map);
	        	material.getMaterialInfo().maps.getTextureNames().remove(map);
	        	addMaterialInitCommand();
	        });
	        
	        GroupPanel groupPanel = new GroupPanel ( 4, label, select, removeTextureButton );
			webComponentPanel.addElement(groupPanel);
		}
	}

	private void addValuePanels(List<Component> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.setLayout(new FlowLayout());

        webComponentPanel.addElement(new WebFormattedVec3Field("Diffuse", material.getDiffuse()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setDiffuse(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Diffuse", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setDiffuse(color);
			}
		}));

        webComponentPanel.addElement(new WebFormattedVec3Field("Ambient", material.getAmbient()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setAmbient(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Ambient", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setAmbient(color);
			}
		}));
        
        webComponentPanel.addElement(new WebFormattedVec3Field("SpecularColor", material.getSpecular()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setSpecular(current);
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("SpecularColor", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setSpecular(color);
			}
		}));
        {
	        LimitedWebFormattedTextField specularExponentInput = new LimitedWebFormattedTextField(1, 10000) {
				@Override
				public void onChange(float currentValue) {
					material.setSpecularCoefficient(currentValue);
				}
			};
			specularExponentInput.setValue(material.getSpecularCoefficient());
	        GroupPanel groupPanel = new GroupPanel ( 4, new WebLabel("Specular Power"), specularExponentInput );
	        TooltipManager.setTooltip ( groupPanel, "1 soft, 100 hard highlights", TooltipWay.up );
	        webComponentPanel.addElement(groupPanel);
        }
        {
	        LimitedWebFormattedTextField reflectiveNessInput = new LimitedWebFormattedTextField(0, 1) {
				@Override
				public void onChange(float currentValue) {
					material.setReflectiveness(currentValue);
				}
			};
			reflectiveNessInput.setValue(material.getReflectiveness());
	        GroupPanel groupPanel = new GroupPanel ( 4, new WebLabel("Reflectiveness"), reflectiveNessInput );
	        webComponentPanel.addElement(groupPanel);
        }
        {
            LimitedWebFormattedTextField glossinessInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setGlossiness(currentValue);
    			}
    		};
    		glossinessInput.setValue(material.getGlossiness());
            GroupPanel groupPanelGlossiness = new GroupPanel ( 4, new WebLabel("Glossiness"), glossinessInput );
            webComponentPanel.addElement(groupPanelGlossiness);
        }
		
		panels.add(webComponentPanel);
	}

	private void addShaderChoosers(List<Component> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.setLayout(new FlowLayout());
		{
        	final WebFileChooserField vertexShaderChooser = new WebFileChooserField ();
        	vertexShaderChooser.setSelectedFile(new File(Program.getDirectory() + material.getVertexShader()));
        	vertexShaderChooser.setPreferredWidth ( 200 );
        	vertexShaderChooser.setPreferredHeight( 20 );
        	vertexShaderChooser.addSelectedFilesListener(new FilesSelectionListener() {
				
				@Override
				public void selectionChanged(List<File> files) {
					File chosenFile = files.get(0);
					String fileName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
					
					File shaderFileInWorkDir = new File(Program.getDirectory() + fileName + ".glsl");
					
					copyShaderIfNotPresent(chosenFile, shaderFileInWorkDir);
					material.setVertexShader(fileName + ".glsl");
		        	addMaterialInitCommand();
				}

			});
        	WebButton copyFromDefaultButton = new WebButton("Copy from default");
        	copyFromDefaultButton.addActionListener(e -> {
        		Object selection = WebOptionPane.showInputDialog( this, "Vertexshader name: ", "Copy Shader", WebOptionPane.QUESTION_MESSAGE, null, null, "default" );
	        	if(selection != null) {
	        		try {
						world.getRenderer().getProgramFactory().copyDefaultVertexShaderToFile(selection.toString());
						material.setVertexShader(selection.toString() + ".glsl");
			        	addMaterialInitCommand();
					} catch (Exception e1) {
						e1.printStackTrace();
						showNotification(NotificationIcon.error, "Not able to set vertex shader");
					}
	        	}
        	});
            GroupPanel vertexShaderPanel = new GroupPanel ( 4, new WebLabel("VertexShader"), vertexShaderChooser, copyFromDefaultButton );
            vertexShaderPanel.setPreferredWidth ( 200 );
            vertexShaderPanel.setPreferredHeight( 20 );
            vertexShaderPanel.setLayout(new GridLayout(1,2));
        	webComponentPanel.add(vertexShaderPanel);
        }
        {
        	final WebFileChooserField fragmentShaderChooser = new WebFileChooserField ();
        	fragmentShaderChooser.setSelectedFile(new File(Program.getDirectory() + material.getFragmentShader()));
        	fragmentShaderChooser.setPreferredWidth ( 200 );
        	fragmentShaderChooser.setPreferredHeight( 20 );
        	fragmentShaderChooser.addSelectedFilesListener(new FilesSelectionListener() {
				
				@Override
				public void selectionChanged(List<File> files) {
					File chosenFile = files.get(0);
					String fileName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
					
					File shaderFileInWorkDir = new File(Program.getDirectory() + fileName + ".glsl");
					
					copyShaderIfNotPresent(chosenFile, shaderFileInWorkDir);
					material.setFragmentShader(fileName + ".glsl");
		        	addMaterialInitCommand();
				}

			});
        	WebButton copyFromDefaultButton = new WebButton("Copy from default");
        	copyFromDefaultButton.addActionListener(e -> {
        		Object selection = WebOptionPane.showInputDialog( this, "Fragmentshader name: ", "Copy Shader", WebOptionPane.QUESTION_MESSAGE, null, null, "default" );
	        	if(selection != null) {
	        		try {
						world.getRenderer().getProgramFactory().copyDefaultFragmentShaderToFile(selection.toString());
						material.setFragmentShader(selection.toString() + ".glsl");
			        	addMaterialInitCommand();
					} catch (Exception e1) {
						e1.printStackTrace();
						showNotification(NotificationIcon.error, "Not able to set fragment shader");
					}
	        	}
        	});
            GroupPanel fragmentShaderPanel = new GroupPanel ( 4, new WebLabel("FragmentShader"), fragmentShaderChooser, copyFromDefaultButton );
            fragmentShaderPanel.setPreferredWidth ( 200 );
            fragmentShaderPanel.setPreferredHeight( 20 );
            fragmentShaderPanel.setLayout(new GridLayout(1,2));
        	webComponentPanel.add(fragmentShaderPanel);
        }
        
        panels.add(webComponentPanel);
	}

	private void copyShaderIfNotPresent(File chosenFile, File shaderFileInWorkDir) {
		if (!shaderFileInWorkDir.exists()) {
			try {
				FileUtils.copyFile(chosenFile, shaderFileInWorkDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void addMaterialInitCommand() {
		SynchronousQueue<MaterialResult> queue = world.getRenderer().addCommand(new InitMaterialCommand(material));
		
		MaterialResult result = null;
		try {
			result = queue.poll(1, TimeUnit.MINUTES);
		} catch (Exception e1) {
			e1.printStackTrace();
			showNotification(NotificationIcon.error, "Not able to change material");
		}
		
		if (!result.isSuccessful()) {
			showNotification(NotificationIcon.error, "Not able to change material");
		} else {
			showNotification(NotificationIcon.plus, "Material changed");
			if(parent != null) {
				parent.refreshMaterialTab();	
			}
			init(result.material);
		}
	}
	
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
