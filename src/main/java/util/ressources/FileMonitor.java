package util.ressources;

import renderer.Renderer;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class FileMonitor {
	
	private static FileMonitor instance;
	
	public FileAlterationMonitor monitor;
	public volatile boolean running = false;
	// TODO: Interface for hot reload deactivation...
	
	private Renderer renderer;
	
	static {
		instance = new FileMonitor(500);
		try {
			instance.monitor.start();
			instance.running = true;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static FileMonitor getInstance() {
		return instance;
	}
	
	private FileMonitor(int interval) {
		monitor = new FileAlterationMonitor(interval);
	}
	
	public void add(FileAlterationObserver observer) {
		if (running) {
			try {
				observer.initialize();
				monitor.addObserver(observer);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void checkAndNotify() {
//		StopWatch.getInstance().start("CheckAndNotify");
		if (!FileMonitor.getInstance().running) { return; }
		monitor.getObservers().forEach(o -> {o.checkAndNotify();});
//		StopWatch.getInstance().stopAndPrintMS();
	}

}
