package de.hanno.hpengine.util.ressources;

import java.io.File;
import java.util.logging.Logger;

import org.apache.commons.io.monitor.FileAlterationObserver;

public class ReloadOnFileChangeListener<T extends Reloadable> extends OnFileChangeListener {

    private static final Logger LOGGER = Logger.getLogger(ReloadOnFileChangeListener.class.getName());

	private T owner;

	public ReloadOnFileChangeListener(T owner) {
		this.owner = owner;
	}

	@Override
	public void onFileChangeAction(File arg0) {
		if(shouldReload(arg0)) {
			LOGGER.info("Reloading....... " + owner.getName());
			LOGGER.info("because " + arg0.getAbsolutePath() + " was changed.");
			owner.reload();
		}
	}
	
	public boolean shouldReload(File changedFile) {
		return true;
	}

	public static void createAndAddToMonitor(String path, Reloadable loadable) {
		createAndAddToMonitor(new File(path), loadable);
	}
	public static void createAndAddToMonitor(File directory, Reloadable loadable) {
		FileAlterationObserver observer = new FileAlterationObserver(directory);
		observer.addListener(new ReloadOnFileChangeListener<>(loadable));
		FileMonitor.getInstance().add(observer);
	}
}