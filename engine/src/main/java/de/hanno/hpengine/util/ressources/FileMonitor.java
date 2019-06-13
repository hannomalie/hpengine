package de.hanno.hpengine.util.ressources;


import de.hanno.hpengine.engine.config.Config;
import de.hanno.hpengine.engine.config.SimpleConfig;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

public class FileMonitor {

//    TODO: Make this non-global
	private static FileMonitor instance;
	
	public FileAlterationMonitor monitor;
	public volatile boolean running = false;
	// TODO: Interface for hot reload deactivation...

    static {
		instance = new FileMonitor(500);
		try {
            if(SimpleConfig.isUseFileReloadXXX()) {
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
        if(!SimpleConfig.isUseFileReloadXXX()) {
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
        if(!SimpleConfig.isUseFileReloadXXX()) {
            return;
        }
		if (!FileMonitor.getInstance().running) { return; }
		monitor.getObservers().forEach(o -> {o.checkAndNotify();});
	}

}
