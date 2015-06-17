package util.gui.input;

import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import com.alee.extended.panel.GridPanel;

public class TitledPanel extends JPanel {
	
	public TitledPanel(String title, Component ... component) {
		super();
		setBorder(BorderFactory.createTitledBorder(title));
		setLayout(new FlowLayout(FlowLayout.LEFT));
		GridPanel buttonGridPanel = new GridPanel(component.length, 1, 5, component);
		add(buttonGridPanel);
	}
}
