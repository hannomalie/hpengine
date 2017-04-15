package de.hanno.hpengine.util.gui;

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
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.event.EntityAddedEvent;
import de.hanno.hpengine.event.MaterialAddedEvent;
import de.hanno.hpengine.renderer.command.LoadModelCommand;
import de.hanno.hpengine.renderer.command.LoadModelCommand.EntityListResult;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AddEntityView extends WebPanel {

	private Engine engine;
	private WebFormattedTextField nameField;
	private DebugFrame debugFrame;
	private WebFrame parentFrame;

	public AddEntityView(Engine engine, WebFrame addEntityFrame, DebugFrame debugFrame) {
		this.engine = engine;
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
			List<File> chosenFiles = WebFileChooser.showMultiOpenDialog("./hp/assets/models/", customizer);
			if(chosenFiles != null) {
				for (File chosenFile : chosenFiles) {
					if (chosenFile != null) {

                        new SwingWorkerWithProgress<EntityListResult>(debugFrame, "Load model", "Unable to load " + chosenFile.getAbsolutePath()) {
							@Override
							public EntityListResult doInBackground() throws Exception {
								EntityListResult result = new LoadModelCommand(chosenFile, nameField.getText()).execute(Engine.getInstance());
								System.out.println("result.entities.size() = " + result.entities.size());
								engine.getScene().addAll(result.entities);
                                Thread.sleep(100);
								return result;
							}

							@Override
							public void done(EntityListResult result) {
							}
						}.execute();
					}
				}
			}
			parentFrame.setVisible(false);
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
