package util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.filechooser.FileNameExtensionFilter;

import engine.World;
import renderer.command.LoadModelCommand;
import renderer.command.LoadModelCommand.EntityListResult;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.swing.Customizer;

public class AddEntitiyView extends WebPanel {

	private World world;
	private WebFormattedTextField nameField;
	private DebugFrame debugFrame;
	private WebFrame parentFrame;

	public AddEntitiyView(World world, WebFrame addEntityFrame, DebugFrame debugFrame) {
		this.world = world;
		this.debugFrame = debugFrame;
		this.parentFrame = addEntityFrame;
		setUndecorated(true);
		this.setSize(600, 300);
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

		Customizer<WebFileChooser> customizer = new Customizer<WebFileChooser>() {
			@Override
			public void customize(WebFileChooser arg0) {
				arg0.setFileFilter(new FileNameExtensionFilter("OBJ models", "obj"));
			}
		};
		
		saveButton.addActionListener(e -> {
			List<File> chosenFiles = WebFileChooser.showMultiOpenDialog(".\\hp\\assets\\models\\", customizer);
			if(chosenFiles != null) {
				for (File chosenFile : chosenFiles) {
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
				}
			}
			parentFrame.setVisible(false);
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
