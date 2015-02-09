package main.renderer.command;

import main.Transform;
import main.World;
import main.model.ITransformable;
import main.renderer.Result;
import main.scene.EnvironmentProbe;

public class RenderProbeCommand implements Command<Result>, ITransformable {

	EnvironmentProbe probe;
	
	public RenderProbeCommand(EnvironmentProbe probe) {
		this.probe = probe;
	}
	
	@Override
	public Result execute(World world) {
		
		probe.draw(world.getScene().getOctree(), World.light);
		
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

}
