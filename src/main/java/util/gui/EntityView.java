package util.gui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.*;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.swing.JScrollPane;

import component.ModelComponent;
import engine.World;
import engine.model.Entity;
import event.MaterialChangedEvent;
import renderer.Renderer;
import renderer.Result;
import renderer.command.RemoveEntityCommand;
import renderer.material.Material;
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
	protected World world;
	protected WebFormattedTextField nameField;
	protected DebugFrame debugFrame;
	protected Renderer renderer;

	public EntityView(World world, DebugFrame debugFrame, Entity entity) {
		this.world = world;
		this.renderer = world.getRenderer();
		this.debugFrame = debugFrame;
		setUndecorated(true);
		this.setSize(600, 700);
		setMargin(20);

		init(entity);
	}

	private void init(Entity entity) {
		this.entity = entity;
		List<Component> panels = getPanels();

		Component[] components = new Component[panels.size()];
		panels.toArray(components);

		GridPanel gridPanel = new GridPanel ( components.length, 1, components);
		gridPanel.setLayout(new FlowLayout());
		this.removeAll();
		this.add(new JScrollPane(gridPanel));
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
	        	SynchronousQueue<Result> queue = world.getRenderer().addCommand(new RemoveEntityCommand((Entity) entity));
	    		
	    		Result result = null;
	    		try {
	    			result = queue.poll(1, TimeUnit.MINUTES);
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
				WebComboBox materialSelect = new WebComboBox(new Vector<Material>(world.getRenderer().getMaterialFactory().getMaterialsAsList()));
				final Material material = world.getRenderer().getMaterialFactory().getDefaultMaterial();
				if(entity.getComponentOption(ModelComponent.class).isPresent()) {
					entity.getComponent(ModelComponent.class).getMaterial();
				}
				materialSelect.setSelectedIndex(world.getRenderer().getMaterialFactory().getMaterialsAsList().indexOf(material));
				materialSelect.addActionListener(e -> {
					WebComboBox cb = (WebComboBox) e.getSource();
					Material selectedMaterial = world.getRenderer().getMaterialFactory().getMaterialsAsList().get(cb.getSelectedIndex());
					entity.getComponent(ModelComponent.class).setMaterial(selectedMaterial.getName());
					World.getEventBus().post(new MaterialChangedEvent()); // TODO Create own event type
				});
				webComponentPanel.addElement(materialSelect);
			} catch (NullPointerException e) {
				Logger.getGlobal().info("No material selection added for " + entity.getClass() + " " +entity.getName());
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
