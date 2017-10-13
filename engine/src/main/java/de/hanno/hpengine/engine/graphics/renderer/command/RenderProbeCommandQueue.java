package de.hanno.hpengine.engine.graphics.renderer.command;

import de.hanno.hpengine.engine.model.Entity;
import de.hanno.hpengine.engine.transform.Transformable;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class RenderProbeCommandQueue {

	public static volatile int MAX_PROBES_RENDERED_PER_DRAW_CALL = 2;
	
	private BlockingQueue<RenderProbeCommand> workQueue;
	
	public <T extends Transformable> RenderProbeCommandQueue() {
		workQueue = new LinkedBlockingQueue<RenderProbeCommand>();
	}

	public void addProbeRenderCommand(EnvironmentProbe probe) {
		addProbeRenderCommand(probe, false);
	}
	public void addProbeRenderCommand(EnvironmentProbe probe, boolean urgent) {
		if(!urgent) {

			RenderProbeCommand command = new RenderProbeCommand(probe);
			if(contains(command)) { return; }
			
			add(new RenderProbeCommand(probe));
		} else {
			add(new RenderProbeCommand(probe));
		}
	}
	
	private boolean contains(RenderProbeCommand command) {
		boolean result = false;
		for (RenderProbeCommand c : workQueue) {
			if(c.getProbe().getIndex() == command.getProbe().getIndex()) {
				result = true; 
			}
		}
		return result;
	}
	
	private void add(RenderProbeCommand command) {
		if(getRemaining() > 20) { return; }
		workQueue.add(command);
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

	public Optional<RenderProbeCommand> takeNearest(Entity camera) {
		Optional<RenderProbeCommand> result = workQueue.stream().findFirst();
		return result;
	}

	public int getRemaining() {
		return workQueue.size();
	}
}
