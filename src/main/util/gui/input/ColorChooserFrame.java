package main.util.gui.input;

import java.awt.Color;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import main.World;

import org.lwjgl.util.vector.Vector3f;

import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.rootpane.WebFrame;

public abstract class ColorChooserFrame extends WebFrame {
	
	private WebColorChooserPanel colorChooserPanel;

	public ColorChooserFrame() {
		this.setSize(400, 400);
		this.colorChooserPanel = new WebColorChooserPanel(false);
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		colorChooserPanel.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = colorChooserPanel.getColor();
				
				onColorChange(new Vector3f( color.getRed()/255.f,
											color.getGreen()/255.f,
											color.getBlue()/255.f));
			}
		});
		
		this.add(colorChooserPanel);
	}
	
	public abstract void onColorChange(Vector3f color);
	
}
