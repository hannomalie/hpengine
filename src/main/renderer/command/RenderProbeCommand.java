package main.renderer.command;

import main.World;
import main.renderer.Result;
import main.scene.EnvironmentProbe;

public class RenderProbeCommand implements Command<Result> {

	EnvironmentProbe probe;
	
	public RenderProbeCommand(EnvironmentProbe probe) {
		this.probe = probe;
	}
	
	@Override
	public Result execute(World world) {
		
		probe.draw(world.getScene().getOctree(), World.light);
		
		return new Result() {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		};
	}

	public EnvironmentProbe getProbe() {
		return probe;
	}

}
