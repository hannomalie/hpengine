package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.label.WebLabel;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.AreaLight;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import org.joml.Vector4f;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AreaLightView extends EntityView {
	private AreaLight light;

	public AreaLightView(Engine engine, DebugFrame debugFrame, AreaLight light) {
		super(engine, light);
		this.light = light;
	}

	@Override
	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		
		WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
		lightColorChooserPanel.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = lightColorChooserPanel.getColor();
				light.setColor(new Vector4f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f, 1f));
			}
		});
		panels.add(lightColorChooserPanel);
		
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.addElement(new WebButton("Use Light Cam"){{ addActionListener(e -> {
            engine.getSceneManager().setActiveCamera(Engine.getInstance().getLightFactory().getCameraForAreaLight(light));
        });}});
        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
			engine.getSceneManager().restoreWorldCamera();
        });}});
        addRemoveButton(webComponentPanel);

		panels.add(webComponentPanel);
		addAttributesPanel(panels);
		return panels;
	}

	private void addRemoveButton(WebComponentPanel webComponentPanel) {
		WebButton removeProbeButton = new WebButton("Remove Light");
		removeProbeButton.addActionListener(e -> {
            CompletableFuture<Boolean> future = Engine.getInstance().getGpuContext().execute(new FutureCallable() {
                @Override
                public Boolean execute() throws Exception {
                    return Engine.getInstance().getSceneManager().getScene().getAreaLights().remove(light);
                }
            });

			Boolean result;
			try {
				result = future.get(1, TimeUnit.MINUTES);

				if(result.equals(Boolean.TRUE)) {
					showNotification(NotificationIcon.plus, "Light removed");
				} else {
					showNotification(NotificationIcon.error, "Not able to remove lights");
				}
			} catch (Exception e1) {
				e1.printStackTrace();
				showNotification(NotificationIcon.error, "Not able to remove lights");
			}
		});
		
		webComponentPanel.addElement(removeProbeButton);
	}
	
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
