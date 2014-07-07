package main.util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;






import main.World;
import main.model.IEntity;
import main.model.Model;
import main.scene.Scene;
import main.util.gui.input.WebFormattedVec3Field;






import org.apache.commons.io.FilenameUtils;
import org.lwjgl.util.vector.Vector3f;






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

	public AddEntitiyView(World world) {
		this.world = world;
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
			File chosenFile = fileChooser.showOpenDialog();
    		if(chosenFile != null) {
    			List<Model> models = new ArrayList<>();
				try {
					models = world.getRenderer().getOBJLoader().loadTexturedModel(chosenFile);
				} catch (Exception e1) {
					final WebNotificationPopup notificationPopup = new WebNotificationPopup();
	                notificationPopup.setIcon(NotificationIcon.error);
	                notificationPopup.setDisplayTime( 2000 );
	                notificationPopup.setContent(new WebLabel("Not able to load " + chosenFile.getAbsolutePath()));
					NotificationManager.showNotification(notificationPopup);
				}
    			for (int i = 0; i < models.size(); i++) {
    				Model model = models.get(i);
    				String counter = i == 0 ? "" : "_" +i;
        			world.getRenderer().getEntityFactory().getEntity(new Vector3f(), nameField.getText() + counter, model, model.getMaterial());
				}
    		}
		});
		panels.add(saveButton);
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
