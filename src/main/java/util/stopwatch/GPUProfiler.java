package util.stopwatch;

import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33.glQueryCounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class GPUProfiler {

	public static boolean PROFILING_ENABLED = false;
	public static boolean PRINTING_ENABLED = false;

	private static ArrayList<GPUTaskProfile> tasks;
	private static ArrayList<Integer> queryObjects;

	private static int frameCounter;
	private static GPUTaskProfile currentTask;

	private static ArrayList<GPUTaskProfile> completedFrames;
	private static ArrayList<Record> collectedTimes;

	static {
		init();
	}

	private static void init() {
		tasks = new ArrayList<>();
		queryObjects = new ArrayList<>();
		frameCounter = 0;
		completedFrames = new ArrayList<>();
		collectedTimes = new ArrayList<>();
		currentTask = null;
	}

	public static void startFrame() {

		if (currentTask != null) {
			tasks.clear();
			return;
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
				tasks.clear();
				return;
				//throw new IllegalStateException("Error ending frame. Not all tasks finished.");
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
			for (Entry<String, Long> entrySet : frame.getTimesTaken().entrySet()) {
				collectedTimes.add(new Record(entrySet.getKey(), entrySet.getValue()));
			}
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
	
	public static void resetCollectedTimes() {
		collectedTimes.clear();
	}
	
	public static void dumpAverages() {
		dumpAverages(Integer.MAX_VALUE);
	}	
	
	public static void dumpAverages(int sampleCount) {
		
		Map<String, AverageHelper> averages = calculateAverages(sampleCount);
		
		if(averages.isEmpty()) { return; }
		System.out.println("##########################################");
		System.out.println("name\t\t\t\t|  time in ms\t|\tsamples");
		averages.entrySet().stream().forEach(s -> {
			String name = s.getKey();
			while(name.length() < 30) {
				name += " ";
			}
			System.out.println(String.format("%s\t|  %.5f\t|\t%s", name.substring(0, Math.min(name.length(), 30)), (s.getValue().summedTime / s.getValue().count) / 1000 / 1000f, s.getValue().count));
		});
	}

	public static Map<String, AverageHelper> calculateAverages(int sampleCount) {
		Map<String, AverageHelper> averages = new HashMap<>();
		
		for(int i = collectedTimes.size(); i > 0; i--) {
			Record record = collectedTimes.get(i-1);
			AverageHelper averageHelper = averages.get(record.name);
			if(averageHelper == null) {
				averageHelper = new AverageHelper();
				averages.put(record.name, averageHelper);
			}
			if(averageHelper.count < sampleCount) {
				averageHelper.count++;
				averageHelper.summedTime += record.time;
			}
		}
		return averages;
	}
	
	public static void reset() {
		init();
	}

	public static class Record {
		public String name = "";
		public Long time = new Long(0);
		
		public Record(String name, long time) {
			this.name = name;
			this.time = time;
		}
		private Record() {}
	}
	public static class AverageHelper {
		public Integer count = new Integer(0);
		public Long summedTime = new Long(0);
		public Long getAverageInMS() { return (summedTime / count) / 1000 / 1000; }
	}
}