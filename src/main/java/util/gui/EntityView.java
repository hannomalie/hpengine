package util.gui;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.swing.*;

import com.alee.laf.checkbox.WebCheckBox;
import component.ModelComponent;
import engine.AppContext;
import engine.model.Entity;
import event.MaterialChangedEvent;
import renderer.OpenGLContext;
import renderer.Renderer;
import renderer.command.Result;
import renderer.command.RemoveEntityCommand;
import renderer.material.Material;
import renderer.material.MaterialFactory;
import util.gui.input.TransformablePanel;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;

public class EntityView extends WebPanel {

	protected Entity entity;
	protected AppContext appContext;
	protected WebFormattedTextField nameField;
	protected DebugFrame debugFrame;
	protected Renderer renderer;

	public EntityView(AppContext appContext, DebugFrame debugFrame, Entity entity) {
		this.appContext = appContext;
        this.renderer = Renderer.getInstance();
		this.debugFrame = debugFrame;
		setUndecorated(true);
		this.setSize(600, 700);
		setMargin(20);

		init(appContext, entity);
	}

	protected void init(AppContext appContext, Entity entity) {
		this.entity = entity;
		List<Component> panels = getPanels();

		Component[] components = new Component[panels.size()];
		panels.toArray(components);

		GridPanel gridPanel = new GridPanel ( components.length, 1, components);
		gridPanel.setLayout(new FlowLayout());
		this.removeAll();
		JScrollPane scrollPane = new JScrollPane(gridPanel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(32);
		this.add(scrollPane);
		repaint();
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

	        webComponentPanel.addElement(new TransformablePanel<Entity>(renderer, entity));

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
				CompletableFuture<Result> future = OpenGLContext.getInstance().execute(() -> {
					return new RemoveEntityCommand((Entity) entity).execute(appContext);
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
	    			if(debugFrame != null) { debugFrame.refreshSceneTree(); }
	    		}
	        });
	        webComponentPanel.addElement(removeEntityButton);

			try {
				WebComboBox materialSelect = new WebComboBox(new Vector<Material>(MaterialFactory.getInstance().getMaterialsAsList()));
				Material material = MaterialFactory.getInstance().getDefaultMaterial();
				if(entity.getComponentOption(ModelComponent.class).isPresent()) {
					material = entity.getComponent(ModelComponent.class).getMaterial();
				}
				materialSelect.setSelectedIndex(MaterialFactory.getInstance().getMaterialsAsList().indexOf(material));
				materialSelect.addActionListener(e -> {
					WebComboBox cb = (WebComboBox) e.getSource();
					Material selectedMaterial = MaterialFactory.getInstance().getMaterialsAsList().get(cb.getSelectedIndex());
					entity.getComponentOption(ModelComponent.class).ifPresent(c -> c.setMaterial(selectedMaterial.getName()));
					if(entity.hasChildren()) {
						for (Entity child : entity.getChildren()) {
							child.getComponentOption(ModelComponent.class).ifPresent(c -> c.setMaterial(selectedMaterial.getName()));
						}
					}
                    AppContext.getEventBus().post(new MaterialChangedEvent()); // TODO Create own event type
				});
				webComponentPanel.addElement(materialSelect);

			} catch (NullPointerException e) {
				Logger.getGlobal().info("No material selection added for " + entity.getClass() + " " +entity.getName());
			}

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
					temp.init(appContext, newEntity);
				});
				childSelect.setName("Children");
				webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Children"), childSelect));
			}

			if(entity.hasParent()) {
				WebButton parentSelectButton = new WebButton(entity.getParent().getName());
				EntityView temp = this;
				parentSelectButton.addActionListener(e -> {
					temp.init(appContext, entity.getParent());
				});
				webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Parent"), parentSelectButton));

				WebButton parentRemove = new WebButton("Remove Parent");
				parentRemove.addActionListener(e -> {
					entity.removeParent();
				});
				webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Remove Parent"), parentRemove));

			} else {
				WebComboBox parentSelect = new WebComboBox(new Vector<>(appContext.getScene().getEntities()));
				parentSelect.addActionListener(e ->{
					int index = parentSelect.getSelectedIndex();
					Entity newParent = appContext.getScene().getEntities().get(index);
					entity.setParent(newParent);
				});
				webComponentPanel.addElement(new GroupPanel(4, new WebLabel("Select Parent"), parentSelect));
			}
	        panels.add(webComponentPanel);
	        
	        return webComponentPanel;
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
