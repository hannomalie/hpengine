package util.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.filechooser.FileNameExtensionFilter;

import engine.AppContext;
import engine.model.Entity;
import renderer.command.Result;
import renderer.command.Command;

import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.swing.Customizer;

public class LoadEntitiyView extends WebPanel {

	private LoadEntitiyView() { }
	
	public static List<Entity> showDialog(AppContext appContext) {
		
		Customizer<WebFileChooser> customizer = new Customizer<WebFileChooser>() {
			@Override
			public void customize(WebFileChooser arg0) {
				arg0.setFileFilter(new FileNameExtensionFilter("Entity files", "hpentity"));
			}
		};
		
		List<File> chosenFiles = WebFileChooser.showMultiOpenDialog(".\\hp\\assets\\entities\\", customizer);
		List<Entity> entitiesToAdd = new ArrayList();
		if(chosenFiles == null) { return entitiesToAdd; }
		for (File chosenFile : chosenFiles) {
			if(chosenFile != null) {
				Entity entity = appContext.getEntityFactory().readWithoutInit(chosenFile.getName());
				if(entity == null) {
					showError(chosenFile);
					continue;
				}
				SynchronousQueue<Result> queue = appContext.getRenderer().getOpenGLContext().addCommand(new Command<Result>() {
					@Override
					public Result execute(AppContext world) {
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
