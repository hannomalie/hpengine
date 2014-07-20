package main.util.gui;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import main.World;
import main.model.IEntity;
import main.util.gui.input.ButtonInput;
import main.util.gui.input.SliderInput;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.slider.WebSlider;
import com.alee.laf.text.WebFormattedTextField;

public class EntitiyView extends WebPanel {

	private IEntity entity;
	private Quaternion startOrientation;
	private Vector3f startPosition;
	private World world;

	public EntitiyView(World world, IEntity entity) {
		this.entity = entity;
		this.startOrientation = new Quaternion(entity.getOrientation());
		this.startPosition = new Vector3f(entity.getPosition());
		this.world = world;
		setUndecorated(true);
		this.setSize(600, 600);
		setMargin(20);
		
		List<Component> panels = new ArrayList<>();
		
		addAttributesPanel(panels);
        MaterialView materialView = new MaterialView(world, entity.getMaterial());
        panels.add(materialView);
        
        Component[] components = new Component[panels.size()];
        panels.toArray(components);
        
        
        this.add(new GridPanel ( components.length, 1, components));
	}
	
	private void addAttributesPanel(List<Component> panels) {
			
			WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
	        webComponentPanel.setElementMargin ( 4 );

	        addNamePanel(webComponentPanel);

	        webComponentPanel.addElement(new WebFormattedVec3Field("Position", entity.getPosition()) {
				@Override
				public void onValueChange(Vector3f current) {
					entity.setPosition(current);
				}
			});

	        webComponentPanel.addElement(new SliderInput("Orientation X", WebSlider.HORIZONTAL, 0, 360, 0) {
				@Override
				public void onValueChange(int value, int delta) {
					Quaternion amount = new Quaternion();
					Vector3f axis = entity.getRightDirection();
					amount.setFromAxisAngle(new Vector4f(axis.x, axis.y, axis.z, (float) Math.toRadians(delta)));
					entity.setOrientation(Quaternion.mul(entity.getOrientation(), amount, null));
				}
			});
	        webComponentPanel.addElement(new SliderInput("Orientation Y", WebSlider.HORIZONTAL, 0, 360, 0) {
				@Override
				public void onValueChange(int value, int delta) {
					Quaternion amount = new Quaternion();
					Vector3f axis = entity.getUpDirection();
					amount.setFromAxisAngle(new Vector4f(axis.x, axis.y, axis.z, (float) Math.toRadians(delta)));
					entity.setOrientation(Quaternion.mul(entity.getOrientation(), amount, null));
				}
			});
	        webComponentPanel.addElement(new SliderInput("Orientation Z", WebSlider.HORIZONTAL, 0, 360, 0) {
				@Override
				public void onValueChange(int value, int delta) {
					Quaternion amount = new Quaternion();
					Vector3f axis = entity.getViewDirection().negate(null);
					amount.setFromAxisAngle(new Vector4f(axis.x, axis.y, axis.z, (float) Math.toRadians(delta)));
					entity.setOrientation(Quaternion.mul(entity.getOrientation(), amount, null));
				}
			});

	        webComponentPanel.addElement(new ButtonInput("Rotation", "Reset") {
				@Override
				public void onClick(ActionEvent e) {
					entity.setOrientation(startOrientation);
				}
			});
	        webComponentPanel.addElement(new ButtonInput("Position", "Reset") {
				@Override
				public void onClick(ActionEvent e) {
					entity.setPosition(startPosition);
				}
			});
	        
	        webComponentPanel.addElement(new WebFormattedVec3Field("Scale", entity.getScale()) {
				@Override
				public void onValueChange(Vector3f current) {
					entity.setScale(current);
				}
			});
	        webComponentPanel.addElement(new WebFormattedVec3Field("View Direction", entity.getViewDirection()) {
				@Override
				public void onValueChange(Vector3f current) {
					Quaternion temp = new Quaternion();
					temp.setFromAxisAngle(new Vector4f(current.x, current.y, current.z, 0));
					entity.setOrientation(temp);
				}
			});
	        
	        panels.add(webComponentPanel);
	}

	private void addNamePanel(WebComponentPanel webComponentPanel) {
		WebLabel labelName = new WebLabel("Name");
		WebFormattedTextField nameField = new WebFormattedTextField();
		nameField.setValue(entity.getName());
		GroupPanel groupPanel = new GroupPanel ( 4, labelName, nameField );
		
		webComponentPanel.addElement(groupPanel);
	}
}
