package de.hanno.hpengine.util.gui.input;

import com.alee.laf.text.WebTextField;
import org.lwjgl.util.vector.Vector3f;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;


public abstract class Vector3fInput {
    private static final Logger LOGGER = Logger.getLogger(Vector3fInput.class.getName());

	WebTextField field1 = new WebTextField("0");
	WebTextField field2 = new WebTextField("0");
	WebTextField field3 = new WebTextField("0");
	ActionListener al = new ActionListener() {
		
		@Override
		public void actionPerformed(ActionEvent e) {
			onChange(getInput());
		}
	};

	public Vector3fInput(JComponent parent) {
		field1.addActionListener(al);
		field2.addActionListener(al);
		field3.addActionListener(al);
		field1.setColumns(4);
		field2.setColumns(4);
		field3.setColumns(4);
		parent.add(field1);
		parent.add(field2);
		parent.add(field3);
	}
	
	public Vector3f getInput() {
		float one = 0;
		float two = 0;
		float three = 0;
		try {
			one = Float.parseFloat(field1.getText());
			two = Float.parseFloat(field2.getText());
			three = Float.parseFloat(field3.getText());
		} catch (Exception e) {
			LOGGER.severe("Conversion failed");
		}
		
		return new Vector3f(one, two, three);
		 
	}
	
	public abstract void onChange(Vector3f value);
}
