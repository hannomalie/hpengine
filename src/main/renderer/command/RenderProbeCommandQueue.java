package main.renderer.command;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import main.World;
import main.scene.EnvironmentProbe;

public class RenderProbeCommandQueue {

	public static int MAX_PROBES_RENDERED_PER_DRAW_CALL = 2;
	
	private BlockingQueue<RenderProbeCommand> workQueue = new LinkedBlockingQueue<RenderProbeCommand>();
	
	public void addProbeRenderCommand(EnvironmentProbe probe) {
		for (RenderProbeCommand renderProbeCommand : workQueue) {
			if(probe.getIndex() == renderProbeCommand.getProbe().getIndex()) { return; };
		}
		workQueue.add(new RenderProbeCommand(probe));
	}
	
	public Optional<RenderProbeCommand> take() {
		if(!workQueue.isEmpty()) {
			RenderProbeCommand command = workQueue.poll();
			if(command != null) {
				return Optional.of(command); 
			}
		}
		return Optional.empty();
	} 
}
