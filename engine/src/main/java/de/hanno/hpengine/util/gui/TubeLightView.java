package de.hanno.hpengine.util.gui;

import com.alee.laf.colorchooser.WebColorChooserPanel;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.graphics.light.tubelight.TubeLight;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TubeLightView extends EntityView {
	private TubeLight light;

	public TubeLightView(Engine engine, TubeLight light) {
		super(engine, light.getEntity());
		this.light = light;
	}

	@Override
	protected List<Component> getPanels() {
		List<Component> panels = new ArrayList<>();
		
		WebColorChooserPanel lightColorChooserPanel = new WebColorChooserPanel();
		lightColorChooserPanel.addChangeListener(e -> {
            Color color = lightColorChooserPanel.getColor();
            light.setColor(new Vector4f(color.getRed()/255.f,
                    color.getGreen()/255.f,
                    color.getBlue()/255.f, 1f));
        });
		panels.add(lightColorChooserPanel);
		
		addAttributesPanel(panels);
		return panels;
	}
}
