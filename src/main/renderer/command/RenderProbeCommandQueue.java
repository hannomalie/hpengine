package main.renderer.command;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import main.camera.Camera;
import main.model.ITransformable;
import main.scene.EnvironmentProbe;
import main.scene.TransformDistanceComparator;

public class RenderProbeCommandQueue {

	public static int MAX_PROBES_RENDERED_PER_DRAW_CALL = 2;
	
	private BlockingQueue<RenderProbeCommand> workQueue;
	
	public <T extends ITransformable> RenderProbeCommandQueue() {
		workQueue = new LinkedBlockingQueue<RenderProbeCommand>();
	}
	
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

	public Optional<RenderProbeCommand> takeNearest(Camera camera) {
		TransformDistanceComparator<ITransformable> comparator = new TransformDistanceComparator<ITransformable>(camera);
		Optional<RenderProbeCommand> result = workQueue.stream().filter(command -> { return command.getProbe().getBox().contains(camera.getPosition()); }).sorted(comparator).findFirst();
		if(!result.isPresent()) {
			result = workQueue.stream().sorted(comparator).findFirst();
		}
		if(result.isPresent()) {
			workQueue.remove(result.get());
		}
		return result;
	}
}
