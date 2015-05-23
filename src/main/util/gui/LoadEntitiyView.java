package main.util.gui;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.filechooser.FileNameExtensionFilter;

import main.World;
import main.model.Entity;
import main.model.IEntity;
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
import com.alee.utils.swing.Customizer;

public class LoadEntitiyView extends WebPanel {

	private LoadEntitiyView() { }
	
	public static List<IEntity> showDialog(World world) {
		
		Customizer<WebFileChooser> customizer = new Customizer<WebFileChooser>() {
			@Override
			public void customize(WebFileChooser arg0) {
				arg0.setFileFilter(new FileNameExtensionFilter("Entity files", "hpentity"));
			}
		};
		
		List<File> chosenFiles = WebFileChooser.showMultiOpenDialog(".\\hp\\assets\\entities\\", customizer);
		List<main.model.IEntity> entitiesToAdd = new ArrayList();
		if(chosenFiles == null) { return entitiesToAdd; }
		for (File chosenFile : chosenFiles) {
			if(chosenFile != null) {
				Entity entity = (Entity) world.getRenderer().getEntityFactory().readWithoutInit(chosenFile.getName());
				if(entity == null) {
					showError(chosenFile);
					continue;
				}
				SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {
					@Override
					public Result execute(World world) {
						entity.init(world);
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
					result = queue.poll(1, TimeUnit.MINUTES);
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
		
		return entitiesToAdd;
//		world.getScene().addAll(entitiesToAdd);
    		
	}

	private static void showSuccess(File chosenFile) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.plus);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel("Loading successful: " + chosenFile.getAbsolutePath()));
		NotificationManager.showNotification(notificationPopup);
	}

	private static void showError(File chosenFile) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(NotificationIcon.error);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel("Not able to load " + chosenFile.getAbsolutePath()));
		NotificationManager.showNotification(notificationPopup);
	}
}
