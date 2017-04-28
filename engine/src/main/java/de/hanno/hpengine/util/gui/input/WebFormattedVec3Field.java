package de.hanno.hpengine.util.gui.input;

import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.text.WebFormattedTextField;

import java.awt.event.ActionListener;

public abstract class WebFormattedVec3Field extends GroupPanel {
	
	private Vector3f input;
	private String label;
	private WebFormattedTextField inputX;
	private WebFormattedTextField inputY;
	private WebFormattedTextField inputZ;

	public WebFormattedVec3Field(String label, Vector3f input) {
		this.label = label;
		this.input = input;
		
		WebLabel webLabel = new WebLabel(label);
		
        inputX = new WebFormattedTextField();
        inputX.setValue(input.x);
        inputX.setColumns(15);
        inputY = new WebFormattedTextField();
        inputY.setValue(input.y);
        inputY.setColumns(15);
        inputZ = new WebFormattedTextField();
        inputZ.setValue(input.z);
        inputZ.setColumns(15);


        ActionListener defaultValueCleanerActionListener = e -> {
            onValueChange(replaceDotAndSemicolonAndGetAsVector());
        };

        inputX.addActionListener(defaultValueCleanerActionListener);
        inputY.addActionListener(defaultValueCleanerActionListener);
        inputZ.addActionListener(defaultValueCleanerActionListener);
        
        this.add(webLabel);
        this.add(inputX);
        this.add(inputY);
        this.add(inputZ);
	}

    private Vector3f replaceDotAndSemicolonAndGetAsVector() {
        return new Vector3f(replaceDotAndSemicolonAndGetAsFloat(inputX), replaceDotAndSemicolonAndGetAsFloat(inputY), replaceDotAndSemicolonAndGetAsFloat(inputZ));
    }
    private Float replaceDotAndSemicolonAndGetAsFloat(WebFormattedTextField inputField) {
        return Float.valueOf(inputField.getText().replace(",", "").replace(".", ""));
    }

    public abstract void onValueChange(Vector3f current);

	public void setValue(Vector3f position) {
		inputX.setValue(position.x);
		inputY.setValue(position.y);
		inputZ.setValue(position.z);
	}

}
