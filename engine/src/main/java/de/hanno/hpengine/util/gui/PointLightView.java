package de.hanno.hpengine.util.gui;

import com.alee.laf.colorchooser.WebColorChooserPanel;
import de.hanno.hpengine.engine.Engine;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.PointLightMovedEvent;
import de.hanno.hpengine.engine.graphics.light.point.PointLight;
import org.joml.Vector4f;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PointLightView extends EntityView {
	private PointLight light;

	public PointLightView(Engine engine, Entity lightEntity) {
		super(engine, lightEntity);
		this.light = entity.getComponent(PointLight.class);
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
            engine.getEventBus().post(new PointLightMovedEvent());
        });
		JScrollPane lightColorChooserScrollPanel = new JScrollPane(lightColorChooserPanel);
		panels.add(lightColorChooserScrollPanel);
		
		addAttributesPanel(panels);
		return panels;
	}
}
