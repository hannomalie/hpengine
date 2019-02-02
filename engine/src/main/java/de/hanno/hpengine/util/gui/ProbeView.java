package de.hanno.hpengine.util.gui;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.combobox.WebComboBox;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebFormattedTextField;
import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotificationPopup;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.event.ProbesChangedEvent;
import de.hanno.hpengine.engine.graphics.renderer.command.Result;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.EnvironmentProbe.Update;
import de.hanno.hpengine.util.commandqueue.FutureCallable;
import de.hanno.hpengine.util.gui.input.MovablePanel;
import de.hanno.hpengine.util.gui.input.SliderInput;
import de.hanno.hpengine.util.gui.input.WebFormattedVec3Field;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProbeView extends WebPanel {

	private EnvironmentProbe probe;
	private Engine engine;
	private WebFormattedTextField nameField;

	public ProbeView(Engine engine, EnvironmentProbe selected) {
		this.probe = selected;
		this.engine = engine;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<Component> panels = new ArrayList<>();
		
		addAttributesPanel(panels);
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        
        this.add(new GridPanel ( components.length, 1, components));
	}
	
	private void addAttributesPanel(List<Component> panels) {
			
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        addNamePanel(webComponentPanel);

        WebButton removeProbeButton = new WebButton("Remove Probe");
		removeProbeButton.addActionListener(e -> {
			Boolean result = engine.getGpuContext().calculate((Callable<Boolean>) () -> {
				return engine.getScene().getEnvironmentProbeManager().remove(probe);
            });
    		
			if(result) {
				showNotification(NotificationIcon.plus, "Probe removed");
				engine.getEventBus().post(new ProbesChangedEvent());
			} else {
				showNotification(NotificationIcon.error, "Not able to remove probe");
			}
        });

        webComponentPanel.addElement(removeProbeButton);
        webComponentPanel.addElement(new WebButton("Use Probe Cam"){{ addActionListener(e -> {
        	engine.getSceneManager().getScene().setActiveCamera(probe.getSampler().getCamera());
        });}});
        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
        	engine.getSceneManager().getScene().restoreWorldCamera();
        });}});

        webComponentPanel.addElement(new MovablePanel<>(probe.getEntity()));

        webComponentPanel.addElement(new WebFormattedVec3Field("Size", probe.getSize()) {
			@Override
			public void onValueChange(Vector3f current) {
				probe.setSize(current.x, current.y, current.z);
			}
		});
        
        webComponentPanel.addElement(new SliderInput("Weight", WebSlider.HORIZONTAL, 0, 100, (int) (100*probe.getWeight())) {
			@Override public void onValueChange(int value, int delta) {
				probe.setWeight((float) value/100.0f);
                engine.getGpuContext().execute(() -> {
                    engine.getScene().getEnvironmentProbeManager().updateBuffers();
				});
			}
		});
        
        {
            WebComboBox updateSelection = new WebComboBox((EnumSet.allOf(Update.class)).toArray());
            updateSelection.addActionListener(e -> {
            	Update selected = (Update) updateSelection.getSelectedItem();
            	probe.setUpdate(selected);
            });
            updateSelection.setSelectedItem(probe.getEntity().getUpdate());
            GroupPanel groupPanelEnironmentMapType = new GroupPanel ( 4, new WebLabel("Update type"), updateSelection );
            webComponentPanel.addElement(groupPanelEnironmentMapType);
        }
        
        panels.add(webComponentPanel);
	}

	private void addNamePanel(WebComponentPanel webComponentPanel) {
		WebLabel labelName = new WebLabel("Name");
		nameField = new WebFormattedTextField();
		nameField.setValue(probe.getEntity().getName());
		
		WebLabel colorPanel = new WebLabel(" ");
		try {
			colorPanel.setBackground(new Color((int)(probe.getDebugColor().x * 255f), (int)(probe.getDebugColor().y * 255f), (int)(probe.getDebugColor().z * 255f)));
		} catch (Exception e) {
			colorPanel.setBackground(new Color(255, 255, 255));
		}
		colorPanel.setOpaque(true);
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField,
				new WebLabel(String.format("ProbesArrayIndex: %d TexUnitIndex: %d", probe.getIndex(), probe.getTextureUnitIndex(engine.getGpuContext()))));
		
		webComponentPanel.addElement(groupPanel);
		webComponentPanel.addElement(colorPanel);
	}
	
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
