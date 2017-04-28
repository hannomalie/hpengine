package de.hanno.hpengine.util.stopwatch;

import de.hanno.hpengine.util.TypedTuple;

import static org.lwjgl.opengl.GL11.GL_TRUE;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL15.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL15.glGetQueryObjectui;
import static org.lwjgl.opengl.GL33.glGetQueryObjectui64;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ProfilingTask {

	private ProfilingTask parent;

	private String name;

	private int startQuery, endQuery;

	private ArrayList<ProfilingTask> children;
	private long startTimeCpu;
	private long endTimeCpu;

	public ProfilingTask() {
		children = new ArrayList<>();
	}

	public ProfilingTask init(ProfilingTask parent, String name) {

		this.parent = parent;
		this.name = name;
		this.startQuery = GPUProfiler.getQuery();
        this.startTimeCpu = System.nanoTime();

		if (parent != null) {
			parent.addChild(this);
		}

		return this;
	}

	private void addChild(ProfilingTask profilerTask) {
		children.add(profilerTask);
	}

	public ProfilingTask end() {
		this.endTimeCpu = System.nanoTime();
		this.endQuery = GPUProfiler.getQuery();
		return parent;
	}

	public ProfilingTask getParent() {
		return parent;
	}

	public boolean resultsAvailable() {
		return glGetQueryObjectui(endQuery, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE;
	}

	public String getName() {
		return name;
	}

	public int getStartQuery() {
		return startQuery;
	}

	public int getEndQuery() {
		return endQuery;
	}

	public long getStartTime() {
		return glGetQueryObjectui64(startQuery, GL_QUERY_RESULT);
	}

	public long getEndTime() {
		return glGetQueryObjectui64(endQuery, GL_QUERY_RESULT);
	}

	public long getTimeTaken() {
		return getEndTime() - getStartTime();
	}

	public long getTimeTakenCpu() {
		return (endTimeCpu - startTimeCpu);
	}

	public ArrayList<ProfilingTask> getChildren() {
		return children;
	}

	public void reset() {
		children.clear();
	}

	public StringBuilder dump(StringBuilder builder) {
		return dump(0, builder);
	}

	private StringBuilder dump(int indentation, StringBuilder builder) {
		if(GPUProfiler.PRINTING_ENABLED) {
			for (int i = 0; i < indentation; i++) {
                builder.append("    ");
			}
			builder.append(String.format("%s : %.5fms (CPU: %.5fms)", name, getTimeTaken() / 1000f / 1000f, getTimeTakenCpu() / 1000f / 1000f));
            builder.append("\n");
		}
		for (int i = 0; i < children.size(); i++) {
			children.get(i).dump(indentation + 1, builder);
		}

        return builder;
	}
	
	public Map<String, TypedTuple<Long, Long>> getTimesTaken() {
		Map<String, TypedTuple<Long, Long>> result = new HashMap();
		
		if(!name.startsWith("Frame")) {
			result.put(name, new TypedTuple(getTimeTaken(), getTimeTakenCpu()));
		} else {
			result.put("Frame", new TypedTuple(getTimeTaken(), getTimeTakenCpu()));
		}
		for (ProfilingTask gpuTaskProfile : children) {
			result.putAll(gpuTaskProfile.getTimesTaken());
		}	
		
		return result;
	}
}
