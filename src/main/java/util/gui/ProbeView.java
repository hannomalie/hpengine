package util.gui;

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
import engine.AppContext;
import engine.model.Entity;
import event.ProbesChangedEvent;
import org.lwjgl.util.vector.Vector3f;
import renderer.OpenGLContext;
import scene.EnvironmentProbe;
import scene.EnvironmentProbe.Update;
import scene.EnvironmentProbeFactory;
import util.gui.input.MovablePanel;
import util.gui.input.SliderInput;
import util.gui.input.WebFormattedVec3Field;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProbeView extends WebPanel {

	private EnvironmentProbe probe;
	private AppContext appContext;
	private WebFormattedTextField nameField;

	public ProbeView(AppContext appContext, EnvironmentProbe selected) {
		this.probe = selected;
		this.appContext = appContext;
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
			CompletableFuture<Boolean> future = OpenGLContext.getInstance().execute(() -> {
				return EnvironmentProbeFactory.getInstance().remove(probe);
			});
    		
    		Boolean result = null;
    		try {
    			result = future.get(1, TimeUnit.MINUTES);
				if(result.equals(Boolean.TRUE)) {
					showNotification(NotificationIcon.plus, "Probe removed");
					AppContext.getEventBus().post(new ProbesChangedEvent());
				} else {
					showNotification(NotificationIcon.error, "Not able to remove probe");
				}
    		} catch (Exception e1) {
    			e1.printStackTrace();
				showNotification(NotificationIcon.error, "Not able to remove probe");
    		}
        });

        webComponentPanel.addElement(removeProbeButton);
        webComponentPanel.addElement(new WebButton("Use Probe Cam"){{ addActionListener(e -> {
        	appContext.setActiveCamera(probe.getSampler());
        });}});
        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
        	appContext.restoreWorldCamera();
        });}});

        webComponentPanel.addElement(new MovablePanel<Entity>(probe));

        webComponentPanel.addElement(new WebFormattedVec3Field("Size", probe.getSize()) {
			@Override
			public void onValueChange(Vector3f current) {
				probe.setSize(current.x, current.y, current.z);
			}
		});
        
        webComponentPanel.addElement(new SliderInput("Weight", WebSlider.HORIZONTAL, 0, 100, (int) (100*probe.getWeight())) {
			@Override public void onValueChange(int value, int delta) {
				probe.setWeight((float) value/100.0f);
				OpenGLContext.getInstance().execute(() -> {
					EnvironmentProbeFactory.getInstance().updateBuffers();
				});
			}
		});
        
        {
            WebComboBox updateSelection = new WebComboBox((EnumSet.allOf(Update.class)).toArray());
            updateSelection.addActionListener(e -> {
            	Update selected = (Update) updateSelection.getSelectedItem();
            	probe.setUpdate(selected);
            });
            updateSelection.setSelectedItem(probe.getUpdate());
            GroupPanel groupPanelEnironmentMapType = new GroupPanel ( 4, new WebLabel("Update type"), updateSelection );
            webComponentPanel.addElement(groupPanelEnironmentMapType);
        }
        
        panels.add(webComponentPanel);
	}

	private void addNamePanel(WebComponentPanel webComponentPanel) {
		WebLabel labelName = new WebLabel("Name");
		nameField = new WebFormattedTextField();
		nameField.setValue(probe.getName());
		
		WebLabel colorPanel = new WebLabel(" ");
		try {
			colorPanel.setBackground(new Color((int)(probe.getDebugColor().x * 255f), (int)(probe.getDebugColor().y * 255f), (int)(probe.getDebugColor().z * 255f)));
		} catch (Exception e) {
			colorPanel.setBackground(new Color(255, 255, 255));
		}
		colorPanel.setOpaque(true);
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField,
				new WebLabel(String.format("ProbesArrayIndex: %d TexUnitIndex: %d", probe.getIndex(), probe.getTextureUnitIndex())));
		
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
