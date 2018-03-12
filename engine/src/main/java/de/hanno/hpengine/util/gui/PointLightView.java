package de.hanno.hpengine.util.gui;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;

import de.hanno.hpengine.engine.Engine;

import de.hanno.hpengine.engine.component.ComponentMapper;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.event.PointLightMovedEvent;
import de.hanno.hpengine.engine.graphics.light.pointlight.PointLight;
import org.joml.Vector4f;

import com.alee.laf.colorchooser.WebColorChooserPanel;

public class PointLightView extends EntityView {
	private PointLight light;
	ComponentMapper<PointLight> mapper = ComponentMapper.Companion.forClass(PointLight.class);

	public PointLightView(Engine engine, Entity lightEntity) {
		super(engine, lightEntity);
		this.light = mapper.getComponent(lightEntity);
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
