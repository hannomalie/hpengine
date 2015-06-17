package util.gui.input;

import com.alee.laf.text.WebFormattedTextField;

public abstract class LimitedWebFormattedTextField extends WebFormattedTextField {
	
	private float min;
	private float max;

	public LimitedWebFormattedTextField(float min, float max) {
		super();
		this.min = min;
		this.max = max;
		
		this.addActionListener(e -> {
			float value = Float.parseFloat(getText());
			
			if(value < min) {
				value = min;
			} else if(value > max) {
				value = max;
			}
			
			onChange(value);
		});
	}
	
	public abstract void onChange(float currentValue);
	
	

}
