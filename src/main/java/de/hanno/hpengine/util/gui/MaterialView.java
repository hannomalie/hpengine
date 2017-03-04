package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.event.MaterialChangedEvent;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.command.GetMaterialCommand;
import de.hanno.hpengine.renderer.command.InitMaterialCommand;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.Material.MAP;
import de.hanno.hpengine.renderer.material.MaterialInfo;
import de.hanno.hpengine.texture.Texture;
import de.hanno.hpengine.texture.TextureFactory;
import de.hanno.hpengine.util.gui.input.*;
import org.apache.commons.io.FileUtils;
import org.lwjgl.util.vector.Vector3f;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MaterialView extends WebPanel {

	private Material material;
	private WebTextField nameField;

	public MaterialView(Material material) {
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

        WebButton saveButton = new WebButton("Save");
        saveButton.addActionListener(e -> {
        	Material toSave = null;
        	if(!nameField.getText().equals(material.getMaterialInfo().name)) {
        		MaterialInfo newInfo = new MaterialInfo(material.getMaterialInfo()).setName(nameField.getText());
				CompletableFuture<InitMaterialCommand.MaterialResult> future = GraphicsContext.getInstance().execute(() -> {
					return new GetMaterialCommand(newInfo).execute(Engine.getInstance());
				});
				InitMaterialCommand.MaterialResult result;
				try {
        			result = future.get(1, TimeUnit.MINUTES);
					if(result.material != null) {
                        toSave = result.material;
						showNotification(NotificationIcon.plus, "Material changed");
					} else {
						showNotification(NotificationIcon.error, "Not able to change material");
					}
        		} catch (Exception e1) {
        			e1.printStackTrace();
        		}

        	} else {
        		toSave = material;
        		Material.write(toSave, toSave.getMaterialInfo().name);
        	}

        	addMaterialInitCommand(toSave);
        });
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);

        WebScrollPane materialAttributesPane = new WebScrollPane(new GridPanel ( panels.size(), 1, components));
        this.setLayout(new BorderLayout());
        this.add(materialAttributesPane, BorderLayout.CENTER);
        this.add(saveButton, BorderLayout.SOUTH);
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
	        
	        List<Texture> textures = getAllTexturesSorted();
	        WebComboBox select = new WebComboBox(new Vector<Texture>(textures));
	        select.setSelectedIndex(-1);
	        
	        select.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Texture selectedTexture = textures.get(cb.getSelectedIndex());
				if(selectedTexture == null) { return; }
	        	material.getMaterialInfo().maps.put(map, selectedTexture);
	        	addMaterialInitCommand(material);
	        });
	        
	        WebButton removeTextureButton = new WebButton("Remove");
	        removeTextureButton.addActionListener(e -> {
	        	material.getMaterialInfo().maps.getTextures().remove(map);
		        select.setSelectedIndex(-1);
	        	addMaterialInitCommand(material);
	        });

            GroupPanel groupPanel = new GroupPanel(4, label, select, removeTextureButton);
			webComponentPanel.addElement(groupPanel);
		}
	}

	private void addExistingTexturesPanels(WebComponentPanel webComponentPanel) {
		for (MAP map : material.getMaterialInfo().maps.getTextures().keySet()) {
			Texture texture = material.getMaterialInfo().maps.getTextures().get(map);
			
	        WebLabel label = new WebLabel ( map.name() );
	        
	        List<Texture> textures = getAllTexturesSorted();
	        WebComboBox select = new WebComboBox(new Vector<Texture>(textures));
	        
	        int assignedTexture = textures.indexOf(texture);
	        select.setSelectedIndex(assignedTexture);
	        
	        select.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Texture selectedTexture = textures.get(cb.getSelectedIndex());
	        	material.getMaterialInfo().maps.put(map, selectedTexture);
	        	Engine.getEventBus().post(new MaterialChangedEvent(material));
	        });
	        
	        WebButton removeTextureButton = new WebButton("Remove");
	        removeTextureButton.addActionListener(e -> {
	        	material.getMaterialInfo().maps.getTextures().remove(map);
	        	material.getMaterialInfo().maps.getTextureNames().remove(map);
	        	addMaterialInitCommand(material);
	        });
	        
	        GroupPanel groupPanel = new GroupPanel ( 4, label, select, removeTextureButton );
			webComponentPanel.addElement(groupPanel);
		}
	}

	private void addValuePanels(List<Component> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.setLayout(new FlowLayout());

        nameField = new WebTextField(material.getName());
		webComponentPanel.addElement(nameField);
        
        webComponentPanel.addElement(new WebFormattedVec3Field("Diffuse", material.getDiffuse()) {
			@Override
			public void onValueChange(Vector3f current) {
				material.setDiffuse(current);
	        	Engine.getEventBus().post(new MaterialChangedEvent(material));
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Diffuse", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setDiffuse(color);
	        	Engine.getEventBus().post(new MaterialChangedEvent(material));
			}
		}));

//        webComponentPanel.addElement(new WebFormattedVec3Field("SpecularColor", material.getSpecular()) {
//			@Override
//			public void onValueChange(Vector3f current) {
//				material.setSpecular(current);
//			}
//		});
//        webComponentPanel.addElement(new ColorChooserButton("SpecularColor", new ColorChooserFrame() {
//			@Override
//			public void onColorChange(Vector3f color) {
//				material.setSpecular(color);
//			}
//		}));
//        {
//	        LimitedWebFormattedTextField specularExponentInput = new LimitedWebFormattedTextField(1, 10000) {
//				@Override
//				public void onChange(float currentValue) {
//					material.setSpecularCoefficient(currentValue);
//				}
//			};
//			specularExponentInput.setValue(material.getSpecularCoefficient());
//	        GroupPanel groupPanel = new GroupPanel ( 4, new WebLabel("Specular Power"), specularExponentInput );
//	        TooltipManager.setTooltip ( groupPanel, "1 soft, 100 hard highlights", TooltipWay.up );
//	        webComponentPanel.addElement(groupPanel);
//        }
        {
            LimitedWebFormattedTextField roughnessInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setRoughness(currentValue);
    			}
    		};
    		roughnessInput.setValue(material.getRoughness());
            {
            	SliderInput roughnessSliderInput = new SliderInput("Roughness", WebSlider.HORIZONTAL, 0, 100, (int)(material.getRoughness()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					roughnessInput.setValue(((float)value/100f));
    					material.setRoughness(((float)value/100f));
    		        	Engine.getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
                GroupPanel groupPanelRoughness = new GroupPanel ( 4, new WebLabel("Roughness"), roughnessInput, roughnessSliderInput );
                webComponentPanel.addElement(groupPanelRoughness);
            }
        }
        {
            LimitedWebFormattedTextField metallicInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setMetallic(currentValue);
    			}
    		};
    		metallicInput.setValue(material.getMetallic());
            {
            	SliderInput metallicSliderInput = new SliderInput("Metallic", WebSlider.HORIZONTAL, 0, 100, (int)(material.getMetallic()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					metallicInput.setValue(((float)value/100f));
    					material.setMetallic(((float)value/100f));
    		        	Engine.getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
    			
                GroupPanel groupPanelMetallic = new GroupPanel ( 4, new WebLabel("Metallic"), metallicInput, metallicSliderInput );
                webComponentPanel.addElement(groupPanelMetallic);
            }
        }
        {
            LimitedWebFormattedTextField ambientInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setAmbient(currentValue);
    			}
    		};
    		ambientInput.setValue(material.getAmbient());
            {
            	SliderInput ambientSliderInput = new SliderInput("Ambient/Emmissive", WebSlider.HORIZONTAL, 0, 100, (int)(material.getAmbient()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					ambientInput.setValue(((float)value/100f));
    					material.setAmbient(((float)value/100f));
    		        	Engine.getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
    			
                GroupPanel groupPanelAmbient = new GroupPanel(4, new WebLabel("Ambient"), ambientInput, ambientSliderInput);
                webComponentPanel.addElement(groupPanelAmbient);
            }
        }
        {
            LimitedWebFormattedTextField transparencyInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setTransparency(currentValue);
    			}
    		};
    		transparencyInput.setValue(material.getTransparency());
            {
            	SliderInput transparencySliderInput = new SliderInput("Transparency", WebSlider.HORIZONTAL, 0, 100, (int)(material.getTransparency()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					transparencyInput.setValue(((float)value/100f));
    					material.setTransparency(((float)value/100f));
    		        	Engine.getInstance().getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
    			
                GroupPanel groupPanelTransparency = new GroupPanel(4, new WebLabel("Transparency"), transparencyInput, transparencySliderInput);
                webComponentPanel.addElement(groupPanelTransparency);
            }
        }
        {
            LimitedWebFormattedTextField parallaxScaleInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setParallaxScale(currentValue);
    			}
    		};
    		parallaxScaleInput.setValue(material.getParallaxScale());
            {
            	SliderInput parallaxScaleSliderInput = new SliderInput("Parallax Scale", WebSlider.HORIZONTAL, 0, 100, (int)(material.getParallaxScale()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					parallaxScaleInput.setValue(((float)value/100f));
    					material.setParallaxScale(((float)value/100f));
                        Engine.getInstance().getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
    			
                GroupPanel groupPanelParallaxScale = new GroupPanel(4, new WebLabel("Parallax Scale"), parallaxScaleInput, parallaxScaleSliderInput);
                webComponentPanel.addElement(groupPanelParallaxScale);
            }
        }
        {
            LimitedWebFormattedTextField parallaxBiasInput = new LimitedWebFormattedTextField(0, 1) {
    			@Override
    			public void onChange(float currentValue) {
    				material.setParallaxBias(currentValue);
    			}
    		};
    		parallaxBiasInput.setValue(material.getParallaxBias());
            {
            	SliderInput parallaxBiasSliderInput = new SliderInput("Parallax Bias", WebSlider.HORIZONTAL, 0, 100, (int)(material.getParallaxScale()*100)) {
    				
    				@Override
    				public void onValueChange(int value, int delta) {
    					parallaxBiasInput.setValue(((float)value/100f));
    					material.setParallaxBias(((float)value/100f));
                        Engine.getInstance().getEventBus().post(new MaterialChangedEvent(material));
    				}
    			};
    			
                GroupPanel groupPanelParallaxBias = new GroupPanel(4, new WebLabel("Parallax Bias"), parallaxBiasInput, parallaxBiasSliderInput);
                webComponentPanel.addElement(groupPanelParallaxBias);
            }
        }

		{
			WebComboBox materialTypeInput = new WebComboBox((EnumSet.allOf(Material.MaterialType.class)).toArray());
			materialTypeInput.addActionListener(e -> {
				Material.MaterialType selected = (Material.MaterialType) materialTypeInput.getSelectedItem();
				material.setMaterialType(selected);
                Engine.getInstance().getEventBus().post(new MaterialChangedEvent(material));
			});
			materialTypeInput.setSelectedItem(material.getMaterialType());
			GroupPanel materialTypePanel = new GroupPanel(4, new WebLabel("Maeterial Type"), materialTypeInput);
			webComponentPanel.addElement(materialTypePanel);
		}
		{
			WebComboBox environmentMapInput = new WebComboBox((EnumSet.allOf(Material.ENVIRONMENTMAPTYPE.class)).toArray());
			environmentMapInput.addActionListener(e -> {
				Material.ENVIRONMENTMAPTYPE selected = (Material.ENVIRONMENTMAPTYPE) environmentMapInput.getSelectedItem();
				material.setEnvironmentMapType(selected);
			});
			environmentMapInput.setSelectedItem(material.getEnvironmentMapType());
			GroupPanel groupPanelEnironmentMapType = new GroupPanel ( 4, new WebLabel("Environment map type"), environmentMapInput );
			webComponentPanel.addElement(groupPanelEnironmentMapType);
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
	
	private void addMaterialInitCommand(Material material) {
		CompletableFuture<InitMaterialCommand.MaterialResult> future = GraphicsContext.getInstance().execute(() -> {
			return new InitMaterialCommand(material).execute(Engine.getInstance());
		});

		InitMaterialCommand.MaterialResult result;
		try {
			result = future.get(1, TimeUnit.MINUTES);
			if(result.isSuccessful()) {
				showNotification(NotificationIcon.plus, "Material changed");

				init(result.material);
				Engine.getEventBus().post(new MaterialChangedEvent(material));
			} else {
				showNotification(NotificationIcon.error, "Not able to change material");
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			showNotification(NotificationIcon.error, "Not able to change material");
		}
	}

	private List<Texture> getAllTexturesSorted() {
        List<Texture> temp = (List<Texture>) TextureFactory.getInstance().TEXTURES.values().stream().sorted(new Comparator<Texture>() {
			@Override
			public int compare(Texture o1, Texture o2) {
				return (o1.getPath().compareTo(o2.getPath()));
			}
		}).collect(Collectors.toList());
        return temp;
	}
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
