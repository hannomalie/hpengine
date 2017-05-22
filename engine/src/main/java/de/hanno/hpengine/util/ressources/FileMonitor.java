package de.hanno.hpengine.util.ressources;


import de.hanno.hpengine.engine.config.Config;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class FileMonitor {
	
	private static FileMonitor instance;
	
	public FileAlterationMonitor monitor;
	public volatile boolean running = false;
	// TODO: Interface for hot reload deactivation...

    static {
		instance = new FileMonitor(500);
		try {
            if(Config.getInstance().isUseFileReloading()) {
                instance.monitor.start();
                instance.running = true;
            }
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
        if(!Config.getInstance().isUseFileReloading()) {
            return;
        }
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
        if(!Config.getInstance().isUseFileReloading()) {
            return;
        }
		if (!FileMonitor.getInstance().running) { return; }
		monitor.getObservers().forEach(o -> {o.checkAndNotify();});
	}

}
