package main.util.gui.input;

import java.awt.event.ActionEvent;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.model.ITransformable;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.slider.WebSlider;

public class TransformablePanel<T extends ITransformable> extends WebComponentPanel {

	private Quaternion startOrientation;
	private Vector3f startPosition;
	
	public TransformablePanel(T transformable) {
		super(false);
	    this.setElementMargin(4);
		startOrientation = new Quaternion(transformable.getOrientation());
		startPosition = new Vector3f(transformable.getPosition());
	    
	    WebFormattedVec3Field positionField = new WebFormattedVec3Field("Position", transformable.getPosition()) {
			@Override
			public void onValueChange(Vector3f current) {
				transformable.setPosition(current);
			}
		};
		this.addElement(positionField);

        this.addElement(new SliderInput("Orientation X", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotate(new Vector4f(1, 0, 0, delta));
			}
		});
        this.addElement(new SliderInput("Orientation Y", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotate(new Vector4f(0, 1, 0, delta));
			}
		});
        this.addElement(new SliderInput("Orientation Z", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotate(new Vector4f(0, 0, 1, delta));
			}
		});
        
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

        this.addElement(new ButtonInput("Rotation", "Reset") {
			@Override
			public void onClick(ActionEvent e) {
				transformable.setOrientation(startOrientation);
			}
		});
        this.addElement(new ButtonInput("Position", "Reset") {
			@Override
			public void onClick(ActionEvent e) {
				transformable.setPosition(startPosition);
				positionField.setValue(transformable.getPosition());
			}
		});
        
        this.addElement(new WebFormattedVec3Field("Scale", transformable.getScale()) {
			@Override
			public void onValueChange(Vector3f current) {
				transformable.setScale(current);
			}
		});
        this.addElement(new WebFormattedVec3Field("View Direction", transformable.getViewDirection()) {
			@Override
			public void onValueChange(Vector3f current) {
				Quaternion temp = new Quaternion();
				temp.setFromAxisAngle(new Vector4f(current.x, current.y, current.z, 0));
				transformable.setOrientation(temp);
			}
		});
	}

}
