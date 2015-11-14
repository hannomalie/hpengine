package util.gui;

import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileChooserField;
import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.optionpane.WebOptionPane;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.scroll.WebScrollPane;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import component.ModelComponent;
import engine.AppContext;
import engine.model.Entity;
import event.MaterialChangedEvent;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import renderer.command.GetMaterialCommand;
import renderer.command.InitMaterialCommand;
import renderer.command.InitMaterialCommand.MaterialResult;
import renderer.material.Material;
import renderer.material.Material.ENVIRONMENTMAPTYPE;
import renderer.material.Material.MAP;
import renderer.material.MaterialFactory.MaterialInfo;
import shader.Shader;
import texture.Texture;
import util.gui.input.*;

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
	private AppContext appContext;
	private DebugFrame parent;
	private WebTextField nameField;
	private Entity entity;

	public MaterialView(DebugFrame parent, AppContext appContext, Material material, Entity entity) {
		this.parent = parent;
		this.entity = entity;
		this.appContext = appContext;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		init(material);
	}
	public MaterialView(DebugFrame parent, AppContext appContext, Material material) {
		this(parent, appContext, material, null);
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
        	Material toSave = null;
        	if(!nameField.getText().equals(material.getMaterialInfo().name)) {
        		MaterialInfo newInfo = new MaterialInfo(material.getMaterialInfo()).setName(nameField.getText());
				CompletableFuture<MaterialResult> future = OpenGLContext.getInstance().doWithOpenGLContext(() -> {
					return new GetMaterialCommand(newInfo).execute(appContext);
				});
				MaterialResult result;
				try {
        			result = future.get(1, TimeUnit.MINUTES);
					if(result.equals(Boolean.TRUE)) {
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
        	if(entity != null) { entity.getComponent(ModelComponent.class).setMaterial(toSave.getName()); }
        });
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);

        WebScrollPane materialAttributesPane = new WebScrollPane(new GridPanel ( panels.size(), 1, components));
        this.setLayout(new BorderLayout());
        this.add(materialAttributesPane, BorderLayout.CENTER);
        this.add(saveButton, BorderLayout.SOUTH);
	}

	public MaterialView(AppContext appContext, Material material) {
		this(null, appContext, material);
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
	        	material.getMaterialInfo().maps.put(map, selectedTexture);
	        	addMaterialInitCommand(material);
	        });
	        
	        WebButton removeTextureButton = new WebButton("Remove");
	        removeTextureButton.addActionListener(e -> {
	        	material.getMaterialInfo().maps.getTextures().remove(map);
		        select.setSelectedIndex(-1);
	        	addMaterialInitCommand(material);
	        });

	        GroupPanel groupPanel = new GroupPanel ( 4, label, select, removeTextureButton );
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
	        	AppContext.getEventBus().post(new MaterialChangedEvent());
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
	        	AppContext.getEventBus().post(new MaterialChangedEvent());
			}
		});
        webComponentPanel.addElement(new ColorChooserButton("Diffuse", new ColorChooserFrame() {
			@Override
			public void onColorChange(Vector3f color) {
				material.setDiffuse(color);
	        	AppContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	AppContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	AppContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	AppContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	appContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	appContext.getEventBus().post(new MaterialChangedEvent());
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
    		        	appContext.getEventBus().post(new MaterialChangedEvent());
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
				appContext.getEventBus().post(new MaterialChangedEvent());
			});
			materialTypeInput.setSelectedItem(material.getMaterialType());
			GroupPanel materialTypePanel = new GroupPanel(4, new WebLabel("Maeterial Type"), materialTypeInput);
			webComponentPanel.addElement(materialTypePanel);
		}
		{
			WebComboBox environmentMapInput = new WebComboBox((EnumSet.allOf(ENVIRONMENTMAPTYPE.class)).toArray());
			environmentMapInput.addActionListener(e -> {
				ENVIRONMENTMAPTYPE selected = (ENVIRONMENTMAPTYPE) environmentMapInput.getSelectedItem();
				material.setEnvironmentMapType(selected);
			});
			environmentMapInput.setSelectedItem(material.getEnvironmentMapType());
			GroupPanel groupPanelEnironmentMapType = new GroupPanel ( 4, new WebLabel("Environment map type"), environmentMapInput );
			webComponentPanel.addElement(groupPanelEnironmentMapType);
		}
		
		panels.add(webComponentPanel);
	}

	private void addShaderChoosers(List<Component> panels) {
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.setLayout(new FlowLayout());
		{
        	final WebFileChooserField vertexShaderChooser = new WebFileChooserField ();
        	vertexShaderChooser.setSelectedFile(new File(Shader.getDirectory() + material.getVertexShader()));
        	vertexShaderChooser.setPreferredWidth ( 200 );
        	vertexShaderChooser.setPreferredHeight( 20 );
        	vertexShaderChooser.addSelectedFilesListener(new FilesSelectionListener() {
				
				@Override
				public void selectionChanged(List<File> files) {
					File chosenFile = files.get(0);
					String fileName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
					
					File shaderFileInWorkDir = new File(Shader.getDirectory() + fileName + ".glsl");
					
					copyShaderIfNotPresent(chosenFile, shaderFileInWorkDir);
					material.setVertexShader(fileName + ".glsl");
		        	addMaterialInitCommand(material);
				}

			});
        	WebButton copyFromDefaultButton = new WebButton("Copy from default");
        	copyFromDefaultButton.addActionListener(e -> {
        		Object selection = WebOptionPane.showInputDialog( this, "Vertexshader name: ", "Copy Shader", WebOptionPane.QUESTION_MESSAGE, null, null, "default" );
	        	if(selection != null) {
	        		try {
						appContext.getRenderer().getProgramFactory().copyDefaultVertexShaderToFile(selection.toString());
						material.setVertexShader(selection.toString() + ".glsl");
			        	addMaterialInitCommand(material);
					} catch (Exception e1) {
						e1.printStackTrace();
						showNotification(NotificationIcon.error, "Not able to set vertex shader");
					}
	        	}
        	});
        	WebButton deleteShaderButton = new WebButton("X");
        	deleteShaderButton.addActionListener(e -> {
        		material.setVertexShader("");
	        	addMaterialInitCommand(material);
        	});
            GroupPanel vertexShaderPanel = new GroupPanel ( 4, new WebLabel("VertexShader"), vertexShaderChooser, copyFromDefaultButton, deleteShaderButton);
            vertexShaderPanel.setPreferredWidth ( 200 );
            vertexShaderPanel.setPreferredHeight( 20 );
            vertexShaderPanel.setLayout(new GridLayout(1,2));
        	webComponentPanel.add(vertexShaderPanel);
        }
        {
        	final WebFileChooserField fragmentShaderChooser = new WebFileChooserField ();
        	fragmentShaderChooser.setSelectedFile(new File(Shader.getDirectory() + material.getFragmentShader()));
        	fragmentShaderChooser.setPreferredWidth ( 200 );
        	fragmentShaderChooser.setPreferredHeight( 20 );
        	fragmentShaderChooser.addSelectedFilesListener(new FilesSelectionListener() {
				
				@Override
				public void selectionChanged(List<File> files) {
					File chosenFile = files.get(0);
					String fileName = FilenameUtils.getBaseName(chosenFile.getAbsolutePath());
					
					File shaderFileInWorkDir = new File(Shader.getDirectory() + fileName + ".glsl");
					
					copyShaderIfNotPresent(chosenFile, shaderFileInWorkDir);
					material.setFragmentShader(fileName + ".glsl");
		        	addMaterialInitCommand(material);
				}

			});
        	WebButton copyFromDefaultButton = new WebButton("Copy from default");
        	copyFromDefaultButton.addActionListener(e -> {
        		Object selection = WebOptionPane.showInputDialog( this, "Fragmentshader name: ", "Copy Shader", WebOptionPane.QUESTION_MESSAGE, null, null, "default" );
	        	if(selection != null) {
	        		try {
						appContext.getRenderer().getProgramFactory().copyDefaultFragmentShaderToFile(selection.toString());
						material.setFragmentShader(selection.toString() + ".glsl");
			        	addMaterialInitCommand(material);
					} catch (Exception e1) {
						e1.printStackTrace();
						showNotification(NotificationIcon.error, "Not able to set fragment shader");
					}
	        	}
        	});
        	WebButton deleteShaderButton = new WebButton("X");
        	deleteShaderButton.addActionListener(e -> {
        		material.setFragmentShader("");
	        	addMaterialInitCommand(material);
        	});
            GroupPanel fragmentShaderPanel = new GroupPanel ( 4, new WebLabel("FragmentShader"), fragmentShaderChooser, copyFromDefaultButton, deleteShaderButton);
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
	
	private void addMaterialInitCommand(Material material) {
		CompletableFuture<MaterialResult> future = OpenGLContext.getInstance().doWithOpenGLContext(() -> {
			return new InitMaterialCommand(material).execute(appContext);
		});

		MaterialResult result;
		try {
			result = future.get(1, TimeUnit.MINUTES);
			if(result.equals(Boolean.TRUE)) {
				showNotification(NotificationIcon.plus, "Material changed");

				if(parent != null) {
					parent.refreshMaterialTab();
				}
				init(result.material);
				AppContext.getEventBus().post(new MaterialChangedEvent());
			} else {
				showNotification(NotificationIcon.error, "Not able to change material");
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			showNotification(NotificationIcon.error, "Not able to change material");
		}
	}

	private List<Texture> getAllTexturesSorted() {
        List<Texture> temp = (List<Texture>) appContext.getRenderer().getTextureFactory().TEXTURES.values().stream().sorted(new Comparator<Texture>() {
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
