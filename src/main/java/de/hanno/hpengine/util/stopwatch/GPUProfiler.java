package de.hanno.hpengine.util.stopwatch;

import de.hanno.hpengine.util.TypedTuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.lwjgl.opengl.GL15.glGenQueries;
import static org.lwjgl.opengl.GL33.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33.glQueryCounter;

public class GPUProfiler {

    public static volatile boolean DUMP_AVERAGES = false;
    public static boolean PROFILING_ENABLED = false;
	public static boolean PRINTING_ENABLED = false;

	private static ArrayList<ProfilingTask> tasks;
	private static ArrayList<Integer> queryObjects;

	private static int frameCounter;
	private static ProfilingTask currentTask;

	private static ArrayList<ProfilingTask> completedFrames;
	private static ArrayList<Record> collectedTimes;
	private static boolean startFrameCalledThisFrame = false;
    private static volatile boolean dumpRequested = false;
    private static String averagesString = "";

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
        startFrameCalledThisFrame = true;

		if (currentTask != null) {
			tasks.clear();
            currentTask = null;
			return;
		}
		if (PROFILING_ENABLED) {
			currentTask = new ProfilingTask().init(null,
					"Frame " + (++frameCounter));
			tasks.add(currentTask);
		}
	}

	public static void start(String name) {
		if (PROFILING_ENABLED && currentTask != null) {
			currentTask = new ProfilingTask().init(currentTask, name);
			tasks.add(currentTask);
		}
	}

	public static void end() {
		if (PROFILING_ENABLED && currentTask != null) {
			currentTask = currentTask.end();
		}
	}

	public static void endFrame() {
		if (PROFILING_ENABLED) {
			if(!startFrameCalledThisFrame || currentTask == null) { return; }

			if (currentTask.getParent() != null) {
//				tasks.clear();
//				return;
				//throw new IllegalStateException("Error ending frame. Not all tasks finished.");
			}
			currentTask.end();

			tasks.remove(currentTask);

			completedFrames.add(currentTask);
			currentTask = null;
		}
	}

	public static ProfilingTask getFrameResults() {
		if (completedFrames.isEmpty()) {
			return null;
		}

		ProfilingTask frame = completedFrames.get(0);
		if (frame.resultsAvailable()) {
			for (Entry<String, TypedTuple<Long, Long>> entrySet : frame.getTimesTaken().entrySet()) {
				collectedTimes.add(new Record(entrySet.getKey(), entrySet.getValue().getLeft(), entrySet.getValue().getRight()));
			}
			return completedFrames.remove(0);
		} else {
			return null;
		}
	}

	public static int getQuery() {
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
        StringBuilder builder = new StringBuilder("");
        builder.append("##########################################\n");
        builder.append("name\t\t\t|  ms\t|ms cpu\t|\tsamples\n");
		averages.entrySet().stream().forEach(s -> {
            String name = s.getKey();
            while (name.length() < 30) {
                name += " ";
            }
			String clippedName = name.substring(0, Math.min(name.length(), 30));
			long time = s.getValue().summedTime / s.getValue().count;
			long timeCpu = s.getValue().summedTimeCpu / s.getValue().count;
			builder.append(String.format("%s\t| %.5f\t|%.5f\t|\t%s", clippedName, time / 1000 / 1000f, timeCpu / 1000 / 1000f, s.getValue().count));
            builder.append("\n");
        });

        dumpRequested = false;
        averagesString = builder.toString();
//        System.out.println("averagesString = " + averagesString);

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
				averageHelper.summedTimeCpu += record.timeCpu;
			}
		}
		return averages;
	}
	
	public static void reset() {
		init();
	}

    public static void requestDump() {
        dumpRequested = true;
    }

    public static boolean isDumpRequested() {
        return dumpRequested;
    }

    public static String getAveragesString() {
        return averagesString;
    }

    public static String getPrintableResult() {
        return null;
    }

    public static class Record {
		public String name = "";
		public Long time = new Long(0);
		public Long timeCpu = new Long(0);
		
		public Record(String name, long time, long timeCpu) {
			this.name = name;
			this.time = time;
			this.timeCpu = timeCpu;
		}
		private Record() {}
	}
	public static class AverageHelper {
		public Integer count = new Integer(0);
		public Long summedTime = new Long(0);
		public Long summedTimeCpu = new Long(0);
		public Long getAverageInMS() { return (summedTime / count) / 1000 / 1000; }
		public Long getAverageCpuInMS() { return (summedTimeCpu / count) / 1000 / 1000; }
	}
}
