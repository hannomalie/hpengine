package de.hanno.hpengine.util.gui.input;

import java.awt.event.ActionEvent;

import de.hanno.hpengine.engine.model.Transformable;
import org.lwjgl.util.vector.Vector3f;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.slider.WebSlider;

public class MovablePanel<T extends Transformable> extends WebComponentPanel {

	private Vector3f startPosition;
	
	public MovablePanel(T transformable) {
		super(false);
	    this.setElementMargin(4);
		startPosition = new Vector3f(transformable.getPosition());
	    
	    WebFormattedVec3Field positionField = new WebFormattedVec3Field("Position", transformable.getPosition()) {
			@Override
			public void onValueChange(Vector3f current) {
				transformable.setPosition(current);
			}
		};
		this.addElement(positionField);

        this.addElement(new SliderInput("Position X", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = transformable.getRightDirection();
				transformable.move((Vector3f) axis.scale(delta));
				positionField.setValue(transformable.getPosition());
			}
		});
        this.addElement(new SliderInput("Position Y", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = transformable.getUpDirection();
				transformable.move((Vector3f) axis.scale(delta));
				positionField.setValue(transformable.getPosition());
			}
		});
        this.addElement(new SliderInput("Position Z", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = transformable.getViewDirection().negate(null);
				transformable.move((Vector3f) axis.scale(delta));
				positionField.setValue(transformable.getPosition());
			}
		});

        this.addElement(new ButtonInput("Position", "Reset") {
			@Override
			public void onClick(ActionEvent e) {
				transformable.setPosition(startPosition);
				positionField.setValue(transformable.getPosition());
			}
		});
	}

}
