package main.util.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import main.World;
import main.model.Entity;
import main.model.IEntity;
import main.renderer.Result;
import main.renderer.command.RemoveEntityCommand;
import main.renderer.material.Material;
import main.texture.Texture;
import main.util.gui.input.ButtonInput;
import main.util.gui.input.SliderInput;
import main.util.gui.input.TransformablePanel;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;

public class EntityView extends WebPanel {

	private Entity entity;
	private World world;
	private WebFormattedTextField nameField;
	private DebugFrame debugFrame;

	public EntityView(World world, DebugFrame debugFrame, Entity entity) {
		this.entity = entity;
		this.world = world;
		this.debugFrame = debugFrame;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<Component> panels = getPanels();
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        
        this.add(new GridPanel ( components.length, 1, components));
	}

	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		addAttributesPanel(panels);
		return panels;
	}
	
	protected void addAttributesPanel(List<Component> panels) {
			
			WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
	        webComponentPanel.setElementMargin ( 4 );

	        addNamePanel(webComponentPanel);
	        
	        webComponentPanel.addElement(new TransformablePanel<IEntity>(entity));

	        WebButton saveEntityButton = new WebButton("Save Entity");
	        saveEntityButton.addActionListener(e -> {
	        	Entity.write(entity, nameField.getText());
	        });
	        webComponentPanel.addElement(saveEntityButton);
	        
	        WebButton removeEntityButton = new WebButton("Remove Entity");
	        removeEntityButton.addActionListener(e -> {
	        	SynchronousQueue<Result> queue = world.getRenderer().addCommand(new RemoveEntityCommand((IEntity) entity));
	    		
	    		Result result = null;
	    		try {
	    			result = queue.poll(1, TimeUnit.MINUTES);
	    		} catch (Exception e1) {
	    			e1.printStackTrace();
	    			showNotification(NotificationIcon.error, "Not able to change material");
	    		}
	    		
	    		if (!result.isSuccessful()) {
	    			showNotification(NotificationIcon.error, "Not able to remove entity");
	    		} else {
	    			showNotification(NotificationIcon.plus, "Entity removed");
	    			if(debugFrame != null) { debugFrame.refreshSceneTree(); }
	    		}
	        });
	        webComponentPanel.addElement(removeEntityButton);
	        
	        WebComboBox materialSelect = new WebComboBox(new Vector<Material>(world.getRenderer().getMaterialFactory().getMaterialsAsList()));
	        materialSelect.setSelectedIndex(world.getRenderer().getMaterialFactory().getMaterialsAsList().indexOf(entity.getMaterial()));
	        materialSelect.addActionListener(e -> {
	        	WebComboBox cb = (WebComboBox) e.getSource();
	        	Material selectedMaterial = world.getRenderer().getMaterialFactory().getMaterialsAsList().get(cb.getSelectedIndex());
	        	entity.setMaterial(selectedMaterial.getName());
	        });
	        webComponentPanel.addElement(materialSelect);
	        
	        panels.add(webComponentPanel);
	}

	private void addNamePanel(WebComponentPanel webComponentPanel) {
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
