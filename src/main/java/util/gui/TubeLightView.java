package util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import engine.Engine;
import renderer.light.TubeLight;

import org.lwjgl.util.vector.Vector4f;

import com.alee.laf.colorchooser.WebColorChooserPanel;

public class TubeLightView extends EntityView {
	private TubeLight light;

	public TubeLightView(Engine engine, DebugFrame debugFrame, TubeLight light) {
		super(engine, light);
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
