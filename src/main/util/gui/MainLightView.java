package main.util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import main.World;
import main.model.IEntity;
import main.renderer.light.DirectionalLight;
import main.renderer.light.PointLight;
import main.util.gui.input.SliderInput;
import main.util.gui.input.TransformablePanel;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.GridPanel;
import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.slider.WebSlider;

public class MainLightView extends EntityView {
	private DirectionalLight light;

	public MainLightView(World world, DebugFrame debugFrame) {
		super(world, debugFrame, world.getRenderer().getLightFactory().getDirectionalLight());
		this.light = world.getRenderer().getLightFactory().getDirectionalLight();
	}

	@Override
	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		
		WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
		lightColorChooserPanel.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = lightColorChooserPanel.getColor();
				light.setColor(new Vector3f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f));
			}
		});
		WebColorChooserPanel ambientLightColorChooserPanel = new WebColorChooserPanel();
		ambientLightColorChooserPanel.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = ambientLightColorChooserPanel.getColor();
				World.AMBIENT_LIGHT.set(new Vector3f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f));
			}
		});
		panels.add(ambientLightColorChooserPanel);
		panels.add(lightColorChooserPanel);

		addAttributesPanel(panels);
		return panels;
	}
	protected WebComponentPanel addAttributesPanel(List<Component> panels) {
		
		WebComponentPanel webComponentPanel = new WebComponentPanel ( true );
        webComponentPanel.setElementMargin ( 4 );

        addNamePanel(webComponentPanel);
        
        WebComponentPanel movablePanel = new WebComponentPanel(false);
        movablePanel.setElementMargin(4);
		movablePanel.addElement(new SliderInput("Orientation X", WebSlider.HORIZONTAL, 0, 3600, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				entity.rotateWorld(new Vector4f(1, 0, 0, 0.01f*delta));
			}
		});
		movablePanel.addElement(new SliderInput("Orientation Y", WebSlider.HORIZONTAL, 0, 3600, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				entity.rotateWorld(new Vector4f(0, 1, 0, 0.01f*delta));
			}
		});
		movablePanel.addElement(new SliderInput("Orientation Z", WebSlider.HORIZONTAL, 0, 3600, 0) {
			@Override
			public void onValueChange(int value, int delta) {
				entity.rotateWorld(new Vector4f(0, 0, 1, 0.01f*delta));
			}
		});
		movablePanel.addElement(new WebFormattedVec3Field("View Direction", entity.getViewDirection()) {
			@Override
			public void onValueChange(Vector3f current) {
				Quaternion temp = new Quaternion();
				temp.setFromAxisAngle(new Vector4f(current.x, current.y, current.z, 0));
				entity.setOrientation(temp);
			}
		});
        
        webComponentPanel.addElement(movablePanel);
        panels.add(webComponentPanel);
        return webComponentPanel;
	}
}
