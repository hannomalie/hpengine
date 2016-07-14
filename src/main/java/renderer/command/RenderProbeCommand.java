package renderer.command;

import engine.Transform;
import engine.AppContext;
import engine.model.Transformable;
import renderer.RenderExtract;
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
	public Result execute(AppContext appContext) {

		// TODO: Remove this f***
		probe.draw(urgent, new RenderExtract().init(appContext.getActiveCamera(), appContext.getScene().getEntities(), appContext.getScene().getDirectionalLight(),true,true,true,false, appContext.getScene().getMinMax()[0], appContext.getScene().getMinMax()[1], null));

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
