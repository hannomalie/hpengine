package main.util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import main.World;
import main.renderer.command.LoadModelCommand;
import main.renderer.command.LoadModelCommand.EntityListResult;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;

public class AddEntitiyView extends WebPanel {

	private World world;
	private WebFormattedTextField nameField;
	private WebFileChooser fileChooser;
	private DebugFrame debugFrame;

	public AddEntitiyView(World world, DebugFrame debugFrame) {
		this.world = world;
		this.debugFrame = debugFrame;
		fileChooser = new WebFileChooser(new File("."));
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<Component> panels = new ArrayList<>();
		
		addAttributesPanel(panels);
		addOkButton(panels);
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        
        this.add(new GridPanel ( components.length, 1, components));
	}
	
	private void addOkButton(List<Component> panels) {
		WebButton saveButton = new WebButton("Ok");
		saveButton.addActionListener(e -> {
			File chosenFile = WebFileChooser.showOpenDialog();
    		if(chosenFile != null) {
				SynchronousQueue<EntityListResult> queue = world.getRenderer().addCommand(new LoadModelCommand(chosenFile, nameField.getText()));
    				
				EntityListResult result = null;
				try {
					result = queue.poll(5, TimeUnit.MINUTES);
				} catch (Exception e1) {
					e1.printStackTrace();
					showError(chosenFile);
				}
				
				if (result == null || !result.isSuccessful()) {
					showError(chosenFile);
				} else {
					world.getScene().addAll(result.entities);
					debugFrame.refreshSceneTree();
					showSuccess(chosenFile);
				}
			}
		});
		panels.add(saveButton);
	}

	private void showSuccess(File chosenFile) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.plus);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel("Loading successful: " + chosenFile.getAbsolutePath()));
		NotificationManager.showNotification(notificationPopup);
	}

	private void showError(File chosenFile) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.error);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel("Not able to load " + chosenFile.getAbsolutePath()));
		NotificationManager.showNotification(notificationPopup);
	}

	private void addAttributesPanel(List<Component> panels) {
			
			WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
	        webComponentPanel.setElementMargin ( 4 );

	        addNamePanel(webComponentPanel);
	        
	        panels.add(webComponentPanel);
	}

	private void addNamePanel(WebComponentPanel webComponentPanel) {
		WebLabel labelName = new WebLabel("Name");
		nameField = new WebFormattedTextField();
		nameField.setValue("Entity_" + System.currentTimeMillis());
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField );
		
		webComponentPanel.addElement(groupPanel);
	}
	    
}
