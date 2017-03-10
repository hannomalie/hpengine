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
import de.hanno.hpengine.component.ModelComponent;
import de.hanno.hpengine.component.PhysicsComponent;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Mesh;
import de.hanno.hpengine.event.EntityAddedEvent;
import de.hanno.hpengine.event.EntityChangedMaterialEvent;
import de.hanno.hpengine.renderer.GraphicsContext;
import de.hanno.hpengine.renderer.Renderer;
import de.hanno.hpengine.renderer.command.RemoveEntityCommand;
import de.hanno.hpengine.renderer.command.Result;
import de.hanno.hpengine.renderer.material.Material;
import de.hanno.hpengine.renderer.material.MaterialFactory;
import org.lwjgl.util.vector.Vector3f;
import de.hanno.hpengine.util.Util;
import de.hanno.hpengine.util.gui.input.LimitedWebFormattedTextField;
import de.hanno.hpengine.util.gui.input.TransformablePanel;
import de.hanno.hpengine.util.gui.input.WebFormattedVec3Field;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class EntityView extends WebPanel {

	protected Entity entity;
	protected Engine engine;
	protected WebFormattedTextField nameField;
	protected Renderer renderer;

	public EntityView(Engine engine, Entity entity) {
		this.engine = engine;
        this.renderer = Renderer.getInstance();
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
        if(entity.getComponentOption(PhysicsComponent.class).isPresent()) {
            WebButton removePhysicsComponent = new WebButton("Remove PhysicsComponent");
            removePhysicsComponent.addActionListener(e -> {
                if(entity.getComponentOption(ModelComponent.class).isPresent()) {
                    entity.removeComponent(entity.getComponent(PhysicsComponent.class));
                }
            });
            physicsPanel.addElement(removePhysicsComponent);
            if(entity.getComponent(PhysicsComponent.class).isInitialized()) {
                javax.vecmath.Vector3f tempVec3 = new javax.vecmath.Vector3f(0,0,0);
                physicsPanel.addElement(new WebFormattedVec3Field("Linear Velocity", Util.fromBullet(entity.getComponent(PhysicsComponent.class).getRigidBody().getLinearVelocity(tempVec3))) {
                    @Override
                    public void onValueChange(Vector3f value) {
                        entity.getComponent(PhysicsComponent.class).getRigidBody().setLinearVelocity(Util.toBullet(value));
                    }
                });
                physicsPanel.addElement(new LimitedWebFormattedTextField("Mass", 0, 10000) {
                    @Override
                    public void onChange(float currentValue) {
                        entity.getComponent(PhysicsComponent.class).getRigidBody().setMassProps(currentValue, new javax.vecmath.Vector3f(0,0,0));
                    }
                });
                WebButton resetTransformButton = new WebButton("Reset Transform");
                resetTransformButton.addActionListener(e -> {
                    entity.getComponent(PhysicsComponent.class).reset();
                });
                physicsPanel.addElement(resetTransformButton);
            }
        } else {
            WebButton addBallPhysicsComponentButton = new WebButton("Add Ball PhysicsComponent");
            addBallPhysicsComponentButton.addActionListener(e -> {
                float radius = 10;
                if(entity.getComponentOption(ModelComponent.class) != null) {
                    radius = entity.getComponent(ModelComponent.class).getBoundingSphereRadius();
                }
                PhysicsComponent physicsComponent = Engine.getInstance().getPhysicsFactory().addBallPhysicsComponent(entity, radius, 0.0f);
            });
            physicsPanel.addElement(addBallPhysicsComponentButton);

            if(entity.getComponentOption(ModelComponent.class).isPresent()) {
                WebButton addMeshPhysicsComponentButton = new WebButton("Add Mesh PhysicsComponent");
                addMeshPhysicsComponentButton.addActionListener(e -> {
                    PhysicsComponent physicsComponent = Engine.getInstance().getPhysicsFactory().addMeshPhysicsComponent(entity, 0.0f);
                    physicsComponent.init();
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
        for(Transform instanceTrafo : entity.getInstances()) {
            instancesPanels.add(new TransformablePanel<>(instanceTrafo));
        }
        WebComponentPanel buttonPanel = new WebComponentPanel();
        buttonPanel.setElementMargin(4);
        WebButton addInstanceButton = new WebButton("Add Instance");
        addInstanceButton.addActionListener(e -> {
            entity.addInstance(new Transform());
            Engine.getEventBus().post(new EntityAddedEvent());
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
        for(Mesh mesh : entity.getComponent(ModelComponent.class).getMeshes()) {
            WebComponentPanel materialSelectionPanel = new WebComponentPanel();
            materialSelectionPanel.setElementMargin(4);
            WebLabel meshName = new WebLabel(mesh.getName());
            materialSelectionPanel.addElement(meshName);
            addMaterialSelect(materialSelectionPanel, e -> {
                WebComboBox cb = (WebComboBox) e.getSource();
                Material selectedMaterial = MaterialFactory.getInstance().getMaterialsAsList().get(cb.getSelectedIndex());
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

        webComponentPanel.addElement(new TransformablePanel<>(entity));

        WebComboBox updateComboBox = new WebComboBox(EnumSet.allOf(Entity.Update.class).toArray(), entity.getUpdate());
        updateComboBox.addActionListener(e -> { entity.setUpdate((Entity.Update) updateComboBox.getSelectedItem()); });
        webComponentPanel.addElement(updateComboBox);

        WebButton saveEntityButton = new WebButton("Save Entity");
        saveEntityButton.addActionListener(e -> {
            Entity.write(entity, nameField.getText());
        });
        webComponentPanel.addElement(saveEntityButton);

        WebButton removeEntityButton = new WebButton("Remove Entity");
        removeEntityButton.addActionListener(e -> {
            CompletableFuture<Result> future = GraphicsContext.getInstance().execute(() -> {
                return new RemoveEntityCommand(entity).execute(engine);
            });

            Result result = null;
            try {
                result = future.get(1, TimeUnit.MINUTES);
            } catch (Exception e1) {
                e1.printStackTrace();
                showNotification(NotificationIcon.error, "Not able to remove entity");
            }

            if (!result.isSuccessful()) {
                showNotification(NotificationIcon.error, "Not able to remove entity");
            } else {
                showNotification(NotificationIcon.plus, "Entity removed");
                Engine.getEventBus().post(new EntityAddedEvent());
            }
        });
        webComponentPanel.addElement(removeEntityButton);

        Material material = MaterialFactory.getInstance().getDefaultMaterial();
        if(entity.getComponentOption(ModelComponent.class).isPresent()) {
            material = entity.getComponent(ModelComponent.class).getMaterial();
        }

        addMaterialSelect(webComponentPanel, e -> {
            WebComboBox cb = (WebComboBox) e.getSource();
            Material selectedMaterial = MaterialFactory.getInstance().getMaterialsAsList().get(cb.getSelectedIndex());
            entity.getComponentOption(ModelComponent.class).ifPresent(c -> c.setMaterial(selectedMaterial.getName()));
            Engine.getEventBus().post(new EntityChangedMaterialEvent(entity));
        }, material);

        if(entity.getComponentOption(ModelComponent.class).isPresent()) {
            webComponentPanel.addElement(new WebCheckBox("Instanced") {{
                this.addActionListener(e -> {entity.getComponent(ModelComponent.class).instanced = !entity.getComponent(ModelComponent.class).instanced;});
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
            WebComboBox parentSelect = new WebComboBox(new Vector<>(engine.getScene().getEntities()));
            parentSelect.addActionListener(e ->{
                int index = parentSelect.getSelectedIndex();
                Entity newParent = engine.getScene().getEntities().get(index);
                entity.setParent(newParent);
            });
            webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Select Parent"), parentSelect));
        }
        panels.add(webComponentPanel);

        return webComponentPanel;
	}

    private void addMaterialSelect(WebComponentPanel webComponentPanel, ActionListener actionListener, Material initialSelection) {
        try {
            WebComboBox materialSelect = new WebComboBox(new Vector<>(MaterialFactory.getInstance().getMaterialsAsList()));

            materialSelect.setSelectedIndex(MaterialFactory.getInstance().getMaterialsAsList().indexOf(initialSelection));
            materialSelect.addActionListener(actionListener);
            webComponentPanel.addElement(materialSelect);

        } catch (NullPointerException e) {
            Logger.getGlobal().info("No material selection added for " + entity.getClass() + " " +entity.getName());
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
