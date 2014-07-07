package main.util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.lwjgl.util.vector.Vector3f;

import main.World;
import main.renderer.command.InitMaterialCommand;
import main.renderer.command.InitMaterialCommand.MaterialResult;
import main.renderer.command.LoadModelCommand;
import main.renderer.command.LoadModelCommand.EntityListResult;
import main.renderer.material.Material;
import main.renderer.material.Material.MAP;
import main.texture.Texture;
import main.util.gui.input.ColorChooserButton;
import main.util.gui.input.ColorChooserFrame;
import main.util.gui.input.LimitedWebFormattedTextField;
import main.util.gui.input.WebFormattedVec3Field;

import com.alee.extended.panel.BorderPanel;
import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;

public class MaterialView extends WebPanel {

	private Material material;
	private World world;

	public MaterialView(World world, Material material) {
		this.material = material;
		this.world = world;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<Component> panels = new ArrayList<>();
		
		addTexturePanel(panels);
        addValuePanels(panels);

        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(e -> {
        	Material.write(material, material.getMaterialInfo().name);
        });
        panels.add(saveButton);
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);

        this.add(new GridPanel ( panels.size(), 1, components));
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
	        	addMaterialInitCommand();
		        select.setSelectedIndex(-1);
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
	        	addMaterialInitCommand();
		        select.setSelectedIndex(-1);
	        });
	        
	        GroupPanel groupPanel = new GroupPanel ( 4, label, select, removeTextureButton );
			webComponentPanel.addElement(groupPanel);
		}
	}

	private void addValuePanels(List<Component> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

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
