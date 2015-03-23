package main.renderer.command;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import main.camera.Camera;
import main.model.ITransformable;
import main.scene.EnvironmentProbe;
import main.scene.TransformDistanceComparator;

public class RenderProbeCommandQueue {

	public static volatile int MAX_PROBES_RENDERED_PER_DRAW_CALL = 2;
	
	private BlockingQueue<RenderProbeCommand> workQueue;
	
	public <T extends ITransformable> RenderProbeCommandQueue() {
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

	public Optional<RenderProbeCommand> takeNearest(Camera camera) {
		TransformDistanceComparator<ITransformable> comparator = new TransformDistanceComparator<ITransformable>(camera);
		Optional<RenderProbeCommand> result = workQueue.stream().filter(command -> { return command.getProbe().getBox().contains(camera.getPosition().negate(null)); }).sorted(comparator).findFirst();
		if(!result.isPresent()) {
			result = workQueue.stream().sorted(comparator).findFirst();
		}
		if(result.isPresent()) {
			workQueue.remove(result.get());
		}
		return result;
	}

	public int getRemaining() {
		return workQueue.size();
	}
}
