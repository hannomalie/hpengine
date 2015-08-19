package util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.alee.laf.button.WebButton;
import engine.World;
import engine.model.Entity;
import renderer.light.DirectionalLight;
import util.gui.input.SliderInput;
import util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.extended.panel.WebComponentPanel;
import com.alee.laf.colorchooser.WebColorChooserPanel;
import com.alee.laf.slider.WebSlider;

public class MainLightView extends EntityView {
	private DirectionalLight light;

	public MainLightView(World world, DebugFrame debugFrame) {
		super(world, debugFrame, world.getRenderer().getLightFactory().getDirectionalLight());
	}

	@Override
	protected void init(World world, Entity entity) {
		this.light = world.getRenderer().getLightFactory().getDirectionalLight();
		super.init(world, entity);
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
        
	    WebFormattedVec3Field positionField = new WebFormattedVec3Field("Position", entity.getPosition()) {
			@Override
			public void onValueChange(Vector3f current) {
				entity.setPosition(current);
			}
		};
		movablePanel.addElement(positionField);
        
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

		movablePanel.addElement(new SliderInput("Position X", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = entity.getRightDirection();
				axis = new Vector3f(1, 0, 0);
				entity.moveInWorld((Vector3f) axis.scale(delta));
				positionField.setValue(entity.getPosition());
			}
		});
		movablePanel.addElement(new SliderInput("Position Y", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = entity.getUpDirection();
				axis = new Vector3f(0, 1, 0);
				entity.moveInWorld((Vector3f) axis.scale(delta));
				positionField.setValue(entity.getPosition());
			}
		});
		movablePanel.addElement(new SliderInput("Position Z", WebSlider.HORIZONTAL, 0, 200, 100) {
			@Override
			public void onValueChange(int value, int delta) {
				Vector3f axis = entity.getViewDirection().negate(null);
				axis = new Vector3f(0, 0, -1);
				entity.moveInWorld((Vector3f) axis.scale(delta));
				positionField.setValue(entity.getPosition());
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

		webComponentPanel.addElement(new WebFormattedVec3Field("Width, Height, Z Max", new Vector3f(light.getCamera().getWidth(),
																									light.getCamera().getHeight(),
																									light.getCamera().getFar()
																)) {
			@Override
			public void onValueChange(Vector3f current) {
				light.getCamera().setWidth(current.x);
				light.getCamera().setHeight(current.y);
				light.getCamera().setFar(current.z);
			}
		});

		webComponentPanel.addElement(new WebButton("Use Light Cam"){{ addActionListener(e -> {
			world.setActiveCamera(light.getCamera());
		});}});
		webComponentPanel.addElement(new WebButton("Use World Cam"){{ addActionListener(e -> {
			world.restoreWorldCamera();
		});}});

        panels.add(webComponentPanel);
        return webComponentPanel;
	}
}
