package main.util.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import main.World;
import main.renderer.Result;
import main.renderer.material.Material;
import main.renderer.material.Material.ENVIRONMENTMAPTYPE;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbe.Update;
import main.util.gui.input.ButtonInput;
import main.util.gui.input.LimitedWebFormattedTextField;
import main.util.gui.input.SliderInput;
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
	private Quaternion startOrientation;
	private Vector3f startPosition;
	private World world;
	private WebFormattedTextField nameField;
	private DebugFrame debugFrame;

	public ProbeView(World world, DebugFrame debugFrame, EnvironmentProbe selected) {
		this.probe = selected;
		this.startOrientation = new Quaternion(selected.getOrientation());
		this.startPosition = new Vector3f(selected.getPosition());
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

	        webComponentPanel.addElement(new WebButton("Use Probe Cam"){{ addActionListener(e -> {
	        	world.setActiveCamera(probe.getCamera());
	        });}});
	        webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
	        	world.setActiveCamera(world.getCamera());
	        });}});

	        webComponentPanel.addElement(new WebFormattedVec3Field("Position", probe.getPosition()) {
				@Override
				public void onValueChange(Vector3f current) {
					probe.setPosition(current);
				}
			});

	        
	        webComponentPanel.addElement(new SliderInput("Position X", WebSlider.HORIZONTAL, 0, 200, 100) {
				@Override
				public void onValueChange(int value, int delta) {
					probe.moveInWorld(new Vector3f(delta, 0, 0));
				}
			});
	        webComponentPanel.addElement(new SliderInput("Position Y", WebSlider.HORIZONTAL, 0, 200, 100) {
				@Override
				public void onValueChange(int value, int delta) {
					probe.moveInWorld(new Vector3f(0, delta, 0));
				}
			});
	        webComponentPanel.addElement(new SliderInput("Position Z", WebSlider.HORIZONTAL, 0, 200, 100) {
				@Override
				public void onValueChange(int value, int delta) {
					probe.moveInWorld(new Vector3f(0, 0, delta));
				}
			});

	        webComponentPanel.addElement(new ButtonInput("Position", "Reset") {
				@Override
				public void onClick(ActionEvent e) {
					probe.setPosition(startPosition);
				}
			});
	        
	        webComponentPanel.addElement(new WebFormattedVec3Field("Scale", probe.getScale()) {
				@Override
				public void onValueChange(Vector3f current) {
					probe.setScale(current);
				}
			});
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
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField );
		
		webComponentPanel.addElement(groupPanel);
	}
	
	private void showNotification(NotificationIcon icon, String text) {
		final WebNotificationPopup notificationPopup = new WebNotificationPopup();
		notificationPopup.setIcon(icon);
		notificationPopup.setDisplayTime( 2000 );
		notificationPopup.setContent(new WebLabel(text));
		NotificationManager.showNotification(notificationPopup);
	}
}
