package de.hanno.hpengine.util.gui.input;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.slider.WebSlider;
import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.model.Transformable;
import org.joml.*;

import java.awt.event.ActionEvent;
import java.lang.Math;

public class TransformablePanel<T extends Matrix4f> extends WebComponentPanel {

	private Quaternionf startOrientation = new Quaternionf();
	private Vector3f startPosition = new Vector3f();

	public TransformablePanel(T transformable) {
		super(false);
	    this.setElementMargin(4);
		startOrientation = new Quaternionf(transformable.getNormalizedRotation(startOrientation));
		startPosition = new Vector3f(transformable.getTranslation(startPosition));

		Vector3f translation = new Vector3f();
		WebFormattedVec3Field positionField = new WebFormattedVec3Field("Position", transformable.getTranslation(translation)) {
			@Override
			public void onValueChange(Vector3f current) {
				transformable.setTranslation(current);
			}
		};
		this.addElement(positionField);

        this.addElement(new SliderInput("Orientation X", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotateX((float) Math.toRadians(delta));
			}
		});
        this.addElement(new SliderInput("Orientation Y", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotateY((float) Math.toRadians(delta));
			}
		});
        this.addElement(new SliderInput("Orientation Z", WebSlider.HORIZONTAL, 0, 360, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				transformable.rotateZ((float) Math.toRadians(delta));
			}
		});
        
        this.addElement(new SliderInput("Position X", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = new Vector3f(1, 0, 0);
				transformable.translateLocal(axis.mul(delta));
				positionField.setValue(transformable.getTranslation(translation));
			}
		});
        this.addElement(new SliderInput("Position Y", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = new Vector3f(0, 1, 0);
				transformable.translateLocal(axis.mul(delta));
				positionField.setValue(transformable.getTranslation(translation));
			}
		});
        this.addElement(new SliderInput("Position Z", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = new Vector3f(0, 0, -1);
				transformable.translateLocal(axis.mul(delta));
				positionField.setValue(transformable.getTranslation(translation));
			}
		});

        this.addElement(new ButtonInput("Rotation", "Reset") {
			@Override
			public void onClick(ActionEvent e) {
				transformable.rotation(startOrientation);
			}
		});
        this.addElement(new ButtonInput("Position", "Reset") {
			@Override
			public void onClick(ActionEvent e) {
				transformable.setTranslation(startPosition);
				positionField.setValue(transformable.getTranslation(translation));
			}
		});

		Vector3f scale = new Vector3f();
		this.addElement(new WebFormattedVec3Field("Scale", transformable.getScale(scale)) {
			@Override
			public void onValueChange(Vector3f current) {
				transformable.scale(current);
			}
		});
        this.addElement(new WebFormattedVec3Field("View Direction", transformable.transformDirection(new Vector3f(0,0,-1))) {
			@Override
			public void onValueChange(Vector3f current) {
				Quaternionf temp = new Quaternionf();
				temp.fromAxisAngleRad(current.x, current.y, current.z, 0);
				transformable.rotate(temp);
			}
		});
	}

}
