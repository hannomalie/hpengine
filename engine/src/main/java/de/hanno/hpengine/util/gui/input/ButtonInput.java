package de.hanno.hpengine.util.gui.input;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.button.WebButton;
import com.alee.laf.label.WebLabel;

public abstract class ButtonInput extends GroupPanel {
	
	private WebLabel label;
	private WebButton button;

	public ButtonInput(String labelString, String buttonString) {
		label = new WebLabel(labelString);
		button = new WebButton(buttonString);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onClick(e);
			}
		});
		this.add(label);
		this.add(button);
	}

	public abstract void onClick(ActionEvent e);

}
