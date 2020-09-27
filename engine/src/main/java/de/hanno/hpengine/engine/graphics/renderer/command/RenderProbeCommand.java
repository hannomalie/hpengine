package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.transform.Transform;
import de.hanno.hpengine.engine.transform.Transformable;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;

public class RenderProbeCommand implements Command<Result>, Transformable {

	EnvironmentProbe probe;
	boolean urgent = false;

	public RenderProbeCommand(EnvironmentProbe probe) {
		this(probe, false);
	}
	public RenderProbeCommand(EnvironmentProbe probe, boolean urgent) {
		this.probe = probe;
		this.urgent = urgent;
	}
	
	@Override
	public Result execute() {
		return new Result();
	}

	public EnvironmentProbe getProbe() {
		return probe;
	}

	@Override
	public Transform getTransform() {
        return probe.getEntity().getTransform();
    }

	@Override
	public void setTransform(Transform transform) { }
	
	public boolean isUrgent() {
		return urgent;
	}

}
