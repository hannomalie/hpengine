package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.label.WebLabel;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class AreaLightView extends EntityView {
	private AreaLight light;

	public AreaLightView(Engine engine, AreaLight light) {
		super(engine, light.getEntity());
		this.light = light;
	}

	@Override
	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		
		WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
		lightColorChooserPanel.addChangeListener(e -> {
            Color color = lightColorChooserPanel.getColor();
            light.setColor(new Vector4f(color.getRed()/255.f,
                    color.getGreen()/255.f,
                    color.getBlue()/255.f, 1f));
        });
		panels.add(lightColorChooserPanel);
		
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );
        webComponentPanel.addElement(new WebButton("Use Light Cam"){{ addActionListener(e -> {
            engine.getSceneManager().getScene().setActiveCamera(engine.getScene().getAreaLightSystem().getCameraForAreaLight(light));
        });}});
        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
			engine.getSceneManager().getScene().restoreWorldCamera();
        });}});
        addRemoveButton(webComponentPanel);

		panels.add(webComponentPanel);
		addAttributesPanel(panels);
		return panels;
	}

	private void addRemoveButton(WebComponentPanel webComponentPanel) {
		WebButton removeProbeButton = new WebButton("Remove Light");
		removeProbeButton.addActionListener(e -> {
			Boolean result = engine.getGpuContext().calculate((Callable<Boolean>)() -> {
				return engine.getSceneManager().getScene().getAreaLights().remove(light);
			});

			if(result.equals(Boolean.TRUE)) {
				showNotification(NotificationIcon.plus, "Light removed");
			} else {
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
