package util.gui.input;

import com.alee.laf.button.WebButton;

public class ColorChooserButton extends WebButton {
	
	private String label;
	private ColorChooserFrame frame;

public ColorChooserButton(String label, ColorChooserFrame frame) {
		super(label);
		this.label = label;
		this.frame = frame;
		this.addActionListener(e -> {
			frame.setVisible(true);
		});
	}

}
