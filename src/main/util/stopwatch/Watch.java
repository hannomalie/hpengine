package main.util.stopwatch;

public class Watch {
	private long start;
	private String description;
	
	public Watch(long start, String description) {
		this.start = start;
		this.description = description;
	}
	private Watch() {}

	public long getStart() {
		return start;
	}
	public String getDescripton() {
		return description;
	}
}
