package util.gui.input;

import com.alee.extended.panel.GroupPanel;
import com.alee.laf.label.WebLabel;
import com.alee.laf.text.WebFormattedTextField;
import com.google.common.base.Strings;

public abstract class LimitedWebFormattedTextField extends GroupPanel {

    WebFormattedTextField textField = new WebFormattedTextField();
	private float min;
	private float max;

    public LimitedWebFormattedTextField(float min, float max) {
        this(null, min, max);

    }

    public LimitedWebFormattedTextField(String label, float min, float max) {
        super();

        if(!Strings.isNullOrEmpty(label)) {
            WebLabel webLabel = new WebLabel(label);
            this.add(webLabel);
        }
        this.min = min;
        this.max = max;

        textField.addActionListener(e -> {
            float value = Float.parseFloat(textField.getText());

            if(value < this.min) {
                value = this.min;
            } else if(value > this.max) {
                value = this.max;
            }

            onChange(value);
        });

        this.add(textField);
    }
	
	public abstract void onChange(float currentValue);


    public void setValue(float value) {
        textField.setValue(value);
    }
}
