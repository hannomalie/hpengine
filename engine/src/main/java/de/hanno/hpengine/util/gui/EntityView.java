package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.checkbox.WebCheckBox;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.tabbedpane.WebTabbedPane;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.component.ModelComponent;
import de.hanno.hpengine.engine.component.PhysicsComponent;
import de.hanno.hpengine.engine.event.EntityChangedMaterialEvent;
import de.hanno.hpengine.engine.graphics.renderer.Renderer;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Instance;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.engine.model.Update;
import de.hanno.hpengine.engine.model.material.Material;
import de.hanno.hpengine.engine.transform.SimpleTransform;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.gui.input.LimitedWebFormattedTextField;
import de.hanno.hpengine.util.gui.input.TransformablePanel;
import de.hanno.hpengine.util.gui.input.WebFormattedVec3Field;
import org.joml.Vector3f;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public class EntityView extends WebPanel {

	protected Entity entity;
	protected Engine engine;
	protected WebFormattedTextField nameField;
	protected Renderer renderer;

	public EntityView(Engine engine, Entity entity) {
		this.engine = engine;
        this.renderer = engine.getRenderer();
		setUndecorated(true);
		this.setSize(600, 700);
		setMargin(20);

		init(entity);
	}

	protected void init(Entity entity) {
		this.entity = entity;
        WebTabbedPane tabbedPane = addEntityPanel();

        addMeshesPanel(entity, tabbedPane);
        addInstancesPanel(entity, tabbedPane);

        addPhysicsPanel(entity, tabbedPane);
		this.add(tabbedPane);
		repaint();
	}

    private void addPhysicsPanel(final Entity entity, WebTabbedPane tabbedPane) {
        WebComponentPanel physicsPanel = new WebComponentPanel();
        if(entity.getComponentOption(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).isPresent()) {
            WebButton removePhysicsComponent = new WebButton("Remove PhysicsComponent");
            removePhysicsComponent.addActionListener(e -> {
                if(entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).isPresent()) {
                    entity.removeComponent(entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY));
                }
            });
            physicsPanel.addElement(removePhysicsComponent);
            if(entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).isInitialized()) {
                javax.vecmath.Vector3f tempVec3 = new javax.vecmath.Vector3f(0,0,0);
                physicsPanel.addElement(new WebFormattedVec3Field("Linear Velocity", Util.fromBullet(entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).getRigidBody().getLinearVelocity(tempVec3))) {
                    @Override
                    public void onValueChange(Vector3f value) {
                        entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).getRigidBody().setLinearVelocity(Util.toBullet(value));
                    }
                });
                physicsPanel.addElement(new LimitedWebFormattedTextField("Mass", 0, 10000) {
                    @Override
                    public void onChange(float currentValue) {
                        entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).getRigidBody().setMassProps(currentValue, new javax.vecmath.Vector3f(0,0,0));
                    }
                });
                WebButton resetTransformButton = new WebButton("Reset Transform");
                resetTransformButton.addActionListener(e -> {
                    entity.getComponent(PhysicsComponent.class, PhysicsComponent.COMPONENT_KEY).reset(engine);
                });
                physicsPanel.addElement(resetTransformButton);
            }
        } else {
            WebButton addBallPhysicsComponentButton = new WebButton("Add Ball PhysicsComponent");
            addBallPhysicsComponentButton.addActionListener(e -> {
                float radius = 10;
                if(entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY) != null) {
                    radius = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getBoundingSphereRadius();
                }
                PhysicsComponent physicsComponent = engine.getPhysicsManager().addBallPhysicsComponent(entity, radius, 0.0f);
            });
            physicsPanel.addElement(addBallPhysicsComponentButton);

            if(entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).isPresent()) {
                WebButton addMeshPhysicsComponentButton = new WebButton("Add StaticMesh PhysicsComponent");
                addMeshPhysicsComponentButton.addActionListener(e -> {
                    PhysicsComponent physicsComponent = engine.getPhysicsManager().addMeshPhysicsComponent(entity, 0.0f);
                    physicsComponent.init(engine);
                    physicsComponent.getRigidBody().setMassProps(0, new javax.vecmath.Vector3f(0,0,0));
                });
                physicsPanel.addElement(addMeshPhysicsComponentButton);
            }
        }
        GridPanel physicsGridPanel = new GridPanel(physicsPanel);
        tabbedPane.addTab("Physics", physicsGridPanel);
    }

    private void addInstancesPanel(Entity entity, WebTabbedPane tabbedPane) {
        List<Component> instancesPanels = new ArrayList<>();
        for(int instanceIndex = 0; instanceIndex < entity.getInstances().size(); instanceIndex++) {
            Instance currentInstance = entity.getInstances().get(instanceIndex);

            TransformablePanel transformablePanel = new TransformablePanel(currentInstance);
            WebComponentPanel materialSelectionPanel = new WebComponentPanel();
            materialSelectionPanel.setElementMargin(4);
            WebLabel meshName = new WebLabel("Instance"+instanceIndex);
            materialSelectionPanel.addElement(meshName);

            Optional<ModelComponent> componentOption = entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY);
            List materials = new ArrayList();
            if(componentOption.isPresent()) {
                for(int i = 0; i < componentOption.get().getModel().getMeshes().size(); i++) {
                    materials.add(((Mesh)(componentOption.get().getModel().getMeshes().get(i))).getMaterial());
                    int finalI = i;
                    addMaterialSelect(materialSelectionPanel, e -> {
                        WebComboBox cb = (WebComboBox) e.getSource();
                        Material selectedMaterial = engine.getMaterialManager().getMaterialsAsList().get(cb.getSelectedIndex());
                        materials.remove(materials.get(finalI));
                        materials.add(finalI, selectedMaterial);
                        currentInstance.setMaterials(materials);
                        Engine.getEventBus().post(new EntityChangedMaterialEvent(entity));
                    }, currentInstance.getMaterials().get(finalI));
                }
            }

            instancesPanels.add(new GroupPanel(transformablePanel, materialSelectionPanel));
        }
        WebComponentPanel buttonPanel = new WebComponentPanel();
        buttonPanel.setElementMargin(4);
        WebButton addInstanceButton = new WebButton("Add Instance");
        addInstanceButton.addActionListener(e -> {
            Entity.addInstance(entity, new SimpleTransform());
//            TODO: Make this possible
//            init(entity);
        });
        buttonPanel.addElement(addInstanceButton);
        instancesPanels.add(buttonPanel);
        GridPanel instancesGridPanel = new GridPanel(instancesPanels.size(), 1, instancesPanels.toArray(new Component[0]));
        JScrollPane instancesScrollPane = new JScrollPane(instancesGridPanel);
        tabbedPane.addTab("Instances", instancesScrollPane);
    }

    private void addMeshesPanel(Entity entity, WebTabbedPane tabbedPane) {
	    if(!entity.hasComponent(ModelComponent.class)) { return; }

        List<Component> meshPanels = new ArrayList<>();
        List<Mesh> meshes = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMeshes();
        for(Mesh mesh : meshes) {
            WebComponentPanel materialSelectionPanel = new WebComponentPanel();
            materialSelectionPanel.setElementMargin(4);
            WebLabel meshName = new WebLabel(mesh.getName());
            materialSelectionPanel.addElement(meshName);
            addMaterialSelect(materialSelectionPanel, e -> {
                WebComboBox cb = (WebComboBox) e.getSource();
                Material selectedMaterial = engine.getMaterialManager().getMaterialsAsList().get(cb.getSelectedIndex());
                mesh.setMaterial(selectedMaterial);
                Engine.getEventBus().post(new EntityChangedMaterialEvent(entity));
            }, mesh.getMaterial());
            meshPanels.add(materialSelectionPanel);
        }
        GridPanel meshesGridPanel = new GridPanel(meshPanels.size(), 1, meshPanels.toArray(new Component[0]));
        JScrollPane meshesPanel = new JScrollPane(meshesGridPanel);
        tabbedPane.addTab("Meshes", meshesPanel);
    }

    private WebTabbedPane addEntityPanel() {
        List<Component> panels = getPanels();

        Component[] components = new Component[panels.size()];
        panels.toArray(components);

        GridPanel gridPanel = new GridPanel ( components.length, 1, components);
        gridPanel.setLayout(new FlowLayout());
        this.removeAll();
        JScrollPane scrollPane = new JScrollPane(gridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(32);

        WebTabbedPane tabbedPane = new WebTabbedPane();
        tabbedPane.addTab("Entity", scrollPane);
        return tabbedPane;
    }

    protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		addAttributesPanel(panels);
		return panels;
	}
	
	protected WebComponentPanel addAttributesPanel(List<Component> panels) {
			
        WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        addNamePanel(webComponentPanel);

        webComponentPanel.addElement(new TransformablePanel(entity));

        WebComboBox updateComboBox = new WebComboBox(EnumSet.allOf(Update.class).toArray(), entity.getUpdate());
        updateComboBox.addActionListener(e -> {
            entity.setUpdate((Update) updateComboBox.getSelectedItem());
            Engine.getEventBus().post(new EntityChangedMaterialEvent(entity));
        });
        webComponentPanel.addElement(updateComboBox);

        WebButton saveEntityButton = new WebButton("Save Entity");
        saveEntityButton.addActionListener(e -> {
            Entity.write(entity, nameField.getText());
        });
        webComponentPanel.addElement(saveEntityButton);

        Material material = engine.getMaterialManager().getDefaultMaterial();
        if(entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).isPresent()) {
            material = entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).getMaterial(engine.getMaterialManager());
        }

        addMaterialSelect(webComponentPanel, e -> {
            WebComboBox cb = (WebComboBox) e.getSource();
            Material selectedMaterial = engine.getMaterialManager().getMaterialsAsList().get(cb.getSelectedIndex());
            entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).ifPresent(c -> c.setMaterial(engine.getMaterialManager(), selectedMaterial.getName()));
            Engine.getEventBus().post(new EntityChangedMaterialEvent(entity));
        }, material);

        if(entity.getComponentOption(ModelComponent.class, ModelComponent.COMPONENT_KEY).isPresent()) {
            webComponentPanel.addElement(new WebCheckBox("Instanced") {{
                this.addActionListener(e -> {entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).instanced = !entity.getComponent(ModelComponent.class, ModelComponent.COMPONENT_KEY).instanced;});
            }});
        }

        if(entity.hasChildren()) {
            WebComboBox childSelect = new WebComboBox(new Vector<>(entity.getChildren()));
            EntityView temp = this;
            childSelect.addActionListener(e ->{
                int index = childSelect.getSelectedIndex();
                Entity newEntity = entity.getChildren().get(index);
                temp.init(newEntity);
            });
            childSelect.setName("Children");
            webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Children"), childSelect));
        }

        if(entity.hasParent()) {
            WebButton parentSelectButton = new WebButton(entity.getParent().getName());
            EntityView temp = this;
            parentSelectButton.addActionListener(e -> {
                temp.init(entity.getParent());
            });
            webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Parent"), parentSelectButton));

            WebButton parentRemove = new WebButton("Remove Parent");
            parentRemove.addActionListener(e -> {
                entity.removeParent();
            });
            webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Remove Parent"), parentRemove));

        } else {
            WebComboBox parentSelect = new WebComboBox(new Vector<>(engine.getSceneManager().getScene().getEntities()));
            parentSelect.addActionListener(e ->{
                int index = parentSelect.getSelectedIndex();
                Entity newParent = engine.getSceneManager().getScene().getEntities().get(index);
                entity.setParent(newParent);
            });
            webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Select Parent"), parentSelect));
        }
        panels.add(webComponentPanel);

        return webComponentPanel;
	}

    private void addMaterialSelect(WebComponentPanel webComponentPanel, ActionListener actionListener, Material initialSelection) {
        try {
            WebComboBox materialSelect = new WebComboBox(new Vector<>(engine.getMaterialManager().getMaterialsAsList()));

            materialSelect.setSelectedIndex(engine.getMaterialManager().getMaterialsAsList().indexOf(initialSelection));
            materialSelect.addActionListener(actionListener);
            webComponentPanel.addElement(materialSelect);

        } catch (NullPointerException e) {
            Logger.getGlobal().info("No materials selection added for " + entity.getClass() + " " +entity.getName());
        }
    }

    protected void addNamePanel(WebComponentPanel webComponentPanel) {
		WebLabel labelName = new WebLabel("Name");
		nameField = new WebFormattedTextField();
		nameField.setValue(entity.getName());
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField );
		
		webComponentPanel.addElement(groupPanel);
	}
	
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
