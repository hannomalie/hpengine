package main.util.gui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import main.World;
import main.model.IEntity;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.GroupPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.panel.WebPanel;
import com.alee.laf.rootpane.WebFrame;
import com.alee.laf.text.WebFormattedTextField;

public class EntitiyView extends WebPanel {

	private IEntity entity;
	private World world;

	public EntitiyView(World world, IEntity entity) {
		this.entity = entity;
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
