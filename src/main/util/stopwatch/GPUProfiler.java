package main.util.stopwatch;

import java.util.ArrayList;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL33.*;

public class GPUProfiler {

	public static boolean PROFILING_ENABLED = false;

	private static ArrayList<GPUTaskProfile> tasks = new ArrayList<>();
	private static ArrayList<Integer> queryObjects = new ArrayList<>();

	private static int frameCounter;
	private static GPUTaskProfile currentTask;

	private static ArrayList<GPUTaskProfile> completedFrames;

	static {
		queryObjects = new ArrayList<>();
		frameCounter = 0;
		completedFrames = new ArrayList<>();
	}

	public static void startFrame() {

		if (currentTask != null) {
			throw new IllegalStateException(
					"Previous frame not ended properly!");
		}
		if (PROFILING_ENABLED) {
			currentTask = new GPUTaskProfile().init(null,
					"Frame " + (++frameCounter), getQuery());
			tasks.add(currentTask);
		}
	}

	public static void start(String name) {
		if (PROFILING_ENABLED && currentTask != null) {
			currentTask = new GPUTaskProfile().init(currentTask, name, getQuery());
			tasks.add(currentTask);
		}
	}

	public static void end() {
		if (PROFILING_ENABLED && currentTask != null) {
			currentTask = currentTask.end(getQuery());
		}
	}

	public static void endFrame() {

		if (PROFILING_ENABLED) {
			if (currentTask.getParent() != null) {
				throw new IllegalStateException(
						"Error ending frame. Not all tasks finished.");
			}
			currentTask.end(getQuery());

			tasks.remove(currentTask);

			completedFrames.add(currentTask);
			currentTask = null;
		}
	}

	public static GPUTaskProfile getFrameResults() {
		if (completedFrames.isEmpty()) {
			return null;
		}

		GPUTaskProfile frame = completedFrames.get(0);
		if (frame.resultsAvailable()) {
			return completedFrames.remove(0);
		} else {
			return null;
		}
	}

	private static int getQuery() {
		int query;
		if (!queryObjects.isEmpty()) {
			query = queryObjects.remove(queryObjects.size() - 1);
		} else {
			query = glGenQueries();
		}

		glQueryCounter(query, GL_TIMESTAMP);

		return query;
	}
}