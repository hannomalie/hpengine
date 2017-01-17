package de.hanno.hpengine.util.gui;

import com.alee.laf.colorchooser.WebColorChooserPanel;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.renderer.light.TubeLight;
import org.lwjgl.util.vector.Vector4f;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
