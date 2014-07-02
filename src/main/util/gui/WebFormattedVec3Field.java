package main.util.gui;

import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.text.WebFormattedTextField;

public abstract class WebFormattedVec3Field extends GroupPanel {
	
	private Vector3f input;
	private String label;

	public WebFormattedVec3Field(String label, Vector3f input) {
		this.label = label;
		this.input = input;
		
		WebLabel webLabel = new WebLabel(label);
		
        WebFormattedTextField inputX = new WebFormattedTextField();
        inputX.setValue(input.x);
        inputX.setColumns(15);
        WebFormattedTextField inputY = new WebFormattedTextField();
        inputY.setValue(input.y);
        inputY.setColumns(15);
        WebFormattedTextField inputZ = new WebFormattedTextField();
        inputZ.setValue(input.z);
        inputZ.setColumns(15);
        

        inputX.addActionListener(e -> {
        	onValueChange(new Vector3f(Float.valueOf(inputX.getText()),Float.valueOf(inputY.getText()),Float.valueOf(inputZ.getText())));
        });
        inputY.addActionListener(e -> {
        	onValueChange(new Vector3f(Float.valueOf(inputX.getText()),Float.valueOf(inputY.getText()),Float.valueOf(inputZ.getText())));
        });
        inputZ.addActionListener(e -> {
        	onValueChange(new Vector3f(Float.valueOf(inputX.getText()),Float.valueOf(inputY.getText()),Float.valueOf(inputZ.getText())));
        });
        
        this.add(webLabel);
        this.add(inputX);
        this.add(inputY);
        this.add(inputZ);
	}
	
	public abstract void onValueChange(Vector3f current);

}
