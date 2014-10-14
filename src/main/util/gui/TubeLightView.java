package main.util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import main.World;
import main.renderer.light.PointLight;
import main.renderer.light.TubeLight;
import main.util.gui.input.WebFormattedVec3Field;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import com.alee.laf.colorchooser.WebColorChooserPanel;

public class TubeLightView extends EntityView {
	private TubeLight light;

	public TubeLightView(World world, DebugFrame debugFrame, TubeLight light) {
		super(world, debugFrame, light);
		this.light = light;
	}

	@Override
	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		
		WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
		lightColorChooserPanel.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent e) {
				Color color = lightColorChooserPanel.getColor();
				light.setColor(new Vector4f(color.getRed()/255.f,
						color.getGreen()/255.f,
						color.getBlue()/255.f, 1f));
			}
		});
		panels.add(lightColorChooserPanel);
		
		addAttributesPanel(panels);
		return panels;
	}
}
