package main.util.gui.input;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.slider.WebSlider;

public abstract class SliderInput extends GroupPanel {
	
	private WebLabel label;
	private WebSlider slider;
	private int lastValue = 0;

	public SliderInput(String labelString, int orientation, int min, int max, int value) {
		label = new WebLabel(labelString);
		slider = new WebSlider(orientation, min, max, value);
		lastValue = value;
		
		slider.addChangeListener(new ChangeListener() {
			
			@Override
			public void stateChanged(ChangeEvent e) {
				int value = slider.getValue();
				int delta = value - lastValue;
				
				WebSlider slider = (WebSlider) e.getSource();
				onValueChange(value, delta);
				
				lastValue = value;
			}
		});
		
		this.add(label);
		this.add(slider);
	}
	
	public abstract void onValueChange(int value, int delta);
}
