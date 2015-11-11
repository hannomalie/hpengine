package util.stopwatch;

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

	public ProfilingTask() {
		children = new ArrayList<>();
	}

	public ProfilingTask init(ProfilingTask parent, String name,
			int startQuery) {

		this.parent = parent;
		this.name = name;
		this.startQuery = startQuery;

		if (parent != null) {
			parent.addChild(this);
		}

		return this;
	}

	private void addChild(ProfilingTask profilerTask) {
		children.add(profilerTask);
	}

	public ProfilingTask end(int endQuery) {
		this.endQuery = endQuery;
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

	public ArrayList<ProfilingTask> getChildren() {
		return children;
	}

	public void reset() {
		children.clear();
	}

	public void dump() {
		dump(0);
	}

	private void dump(int indentation) {
		if(GPUProfiler.PRINTING_ENABLED) {
			for (int i = 0; i < indentation; i++) {
				System.out.print("    ");
			}
			System.out.println(String.format("%s : %.5fms", name, getTimeTaken() / 1000f / 1000f));	
		}
		for (int i = 0; i < children.size(); i++) {
			children.get(i).dump(indentation + 1);
		}
	}
	
	public Map<String, Long> getTimesTaken() {
		Map<String, Long> result = new HashMap();
		
		if(!name.startsWith("Frame")) {
			result.put(name, getTimeTaken());
		} else {
			result.put("Frame", getTimeTaken());
		}
		for (ProfilingTask gpuTaskProfile : children) {
			result.putAll(gpuTaskProfile.getTimesTaken());
		}	
		
		return result;
	}
}