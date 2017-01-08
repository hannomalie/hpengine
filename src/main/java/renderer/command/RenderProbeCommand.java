package renderer.command;

import engine.Transform;
import engine.Engine;
import engine.model.Transformable;
import scene.EnvironmentProbe;

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
	public Result execute(Engine engine) {
		return new Result();
	}

	public EnvironmentProbe getProbe() {
		return probe;
	}

	@Override
	public Transform getTransform() {
		return probe.getTransform();
	}

	@Override
	public void setTransform(Transform transform) { }
	
	public boolean isUrgent() {
		return urgent;
	}

}
