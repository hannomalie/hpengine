package main.util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import main.World;
import main.model.Entity;
import main.renderer.Result;
import main.renderer.command.Command;
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

public class LoadEntitiyView extends WebPanel {

	private World world;
	private WebFormattedTextField nameField;
	private WebFileChooser fileChooser;
	private DebugFrame debugFrame;

	public LoadEntitiyView(World world, DebugFrame debugFrame) {
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
			List<File> chosenFiles = WebFileChooser.showMultiOpenDialog();
			List<main.model.IEntity> entitiesToAdd = new ArrayList();
			for (File chosenFile : chosenFiles) {
				if(chosenFile != null) {
					Entity entity = (Entity) world.getRenderer().getEntityFactory().readWithoutInit(chosenFile.getName());
					entitiesToAdd.add(entity);
					SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
						@Override
						public Result execute(World world) {
							entity.init(world.getRenderer());
							return new Result() {
								@Override
								public boolean isSuccessful() {
									return true;
								}
							};
						}
						
					});
	    				
					Result result = null;
					try {
						result = queue.poll(5, TimeUnit.MINUTES);
					} catch (Exception e1) {
						e1.printStackTrace();
						showError(chosenFile);
					}
					if (result == null || !result.isSuccessful()) {
						showError(chosenFile);
					} else {
						entitiesToAdd.add(entity);
					}
				}
			}
			
			world.getScene().addAll(entitiesToAdd);
			debugFrame.refreshSceneTree();
    		
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
