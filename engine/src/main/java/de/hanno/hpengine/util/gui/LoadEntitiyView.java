package de.hanno.hpengine.util.gui;

import com.alee.laf.filechooser.WebFileChooser;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import com.alee.utils.swing.Customizer;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.EntityFactory;
import de.hanno.hpengine.engine.graphics.renderer.GraphicsContext;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class LoadEntitiyView extends WebPanel {

	private LoadEntitiyView() { }
	
	public static List<Entity> showDialog(Engine engine) {
		
		Customizer<WebFileChooser> customizer = new Customizer<WebFileChooser>() {
			@Override
			public void customize(WebFileChooser arg0) {
				arg0.setFileFilter(new FileNameExtensionFilter("Entity files", "hpentity"));
			}
		};
		
		List<File> chosenFiles = WebFileChooser.showMultiOpenDialog("./hp/assets/entities/", customizer);
		List<Entity> entitiesToAdd = new ArrayList();
		if(chosenFiles == null) { return entitiesToAdd; }
		for (File chosenFile : chosenFiles) {
			if(chosenFile != null) {
                Entity entity = EntityFactory.getInstance().readWithoutInit(chosenFile.getName());
				if(entity == null) {
					showError(chosenFile);
					continue;
				}
				CompletableFuture<Boolean> future = GraphicsContext.getInstance().execute(() -> {
					entity.init();
					return true;
				});

				Boolean result;
				try {
					result = future.get(1, TimeUnit.MINUTES);
					if(result.equals(Boolean.TRUE)) {
						entitiesToAdd.add(entity);
					}
				} catch (Exception e1) {
					e1.printStackTrace();
					showError(chosenFile);
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
