package main.util.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import main.World;
import main.model.IEntity;
import main.renderer.Result;
import main.renderer.command.Command;
import main.renderer.command.RemoveEntityCommand;
import main.renderer.material.Material;
import main.renderer.material.Material.ENVIRONMENTMAPTYPE;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbe.Update;
import main.util.gui.input.ButtonInput;
import main.util.gui.input.LimitedWebFormattedTextField;
import main.util.gui.input.MovablePanel;
import main.util.gui.input.SliderInput;
import main.util.gui.input.TransformablePanel;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

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

public class ProbeView extends WebPanel {

	private EnvironmentProbe probe;
	private World world;
	private WebFormattedTextField nameField;
	private DebugFrame debugFrame;

	public ProbeView(World world, DebugFrame debugFrame, EnvironmentProbe selected) {
		this.probe = selected;
		this.world = world;
		this.debugFrame = debugFrame;
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
	        	SynchronousQueue<Result> queue = world.getRenderer().addCommand(new Command<Result>() {

					@Override
					public Result execute(World world) {
						world.getRenderer().getEnvironmentProbeFactory().remove(probe);
						return new Result() {
							@Override public boolean isSuccessful() { return true; }
						};
					}
	        	});
	    		
	    		Result result = null;
	    		try {
	    			result = queue.poll(1, TimeUnit.MINUTES);
	    		} catch (Exception e1) {
	    			e1.printStackTrace();
	    			showNotification(NotificationIcon.error, "Not able to remove probe");
	    		}
	    		
	    		if (!result.isSuccessful()) {
	    			showNotification(NotificationIcon.error, "Not able to remove probe");
	    		} else {
	    			showNotification(NotificationIcon.plus, "Probe removed");
	    			if(debugFrame != null) { debugFrame.refreshProbeTab(); }
	    		}
	        });

	        webComponentPanel.addElement(removeProbeButton);
	        webComponentPanel.addElement(new WebButton("Use Probe Cam"){{ addActionListener(e -> {
	        	world.setActiveCamera(probe.getCamera());
	        });}});
	        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
	        	world.setActiveCamera(world.getCamera());
	        });}});

	        webComponentPanel.addElement(new MovablePanel<IEntity>(probe));

	        webComponentPanel.addElement(new WebFormattedVec3Field("Size", probe.getSize()) {
				@Override
				public void onValueChange(Vector3f current) {
					probe.setSize(current.x, current.y, current.z);
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
		colorPanel.setBackground(new Color((int)(probe.getDebugColor().x * 255f), (int)(probe.getDebugColor().y * 255f), (int)(probe.getDebugColor().z * 255f)));
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
