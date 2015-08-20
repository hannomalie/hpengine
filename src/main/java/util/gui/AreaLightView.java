package util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import engine.World;
import renderer.command.Result;
import renderer.command.Command;
import renderer.light.AreaLight;

import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.label.WebLabel;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;

public class AreaLightView extends EntityView {
	private AreaLight light;

	public AreaLightView(World world, DebugFrame debugFrame, AreaLight light) {
		super(world, debugFrame, light);
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
        	world.setActiveCamera(world.getRenderer().getLightFactory().getCameraForAreaLight(light));
        });}});
        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
			world.restoreWorldCamera();
        });}});
        addRemoveButton(webComponentPanel);
        
		panels.add(webComponentPanel);
		addAttributesPanel(panels);
		return panels;
	}

	private void addRemoveButton(WebComponentPanel webComponentPanel) {
		WebButton removeProbeButton = new WebButton("Remove Light");
		removeProbeButton.addActionListener(e -> {
        	SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {

				@Override
				public Result execute(World world) {
					world.getRenderer().getLightFactory().getAreaLights().remove(light);
					return new Result();
				}
        	});
    		
    		Result result = null;
    		try {
    			result = queue.poll(1, TimeUnit.MINUTES);
    		} catch (Exception e1) {
    			e1.printStackTrace();
    			showNotification(NotificationIcon.error, "Not able to remove light");
    		}
    		
    		if (!result.isSuccessful()) {
    			showNotification(NotificationIcon.error, "Not able to remove light");
    		} else {
    			showNotification(NotificationIcon.plus, "Light removed");
    			if(debugFrame != null) { debugFrame.refreshAreaLightsTab(); }
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
