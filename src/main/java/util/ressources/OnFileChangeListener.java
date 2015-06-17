package util.ressources;

import java.io.File;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;

public abstract class OnFileChangeListener implements FileAlterationListener {

	@Override
	public void onDirectoryChange(File arg0) {
		
	}

	@Override
	public void onDirectoryCreate(File arg0) {
		
	}

	@Override
	public void onDirectoryDelete(File arg0) {
		
	}

	@Override
	public void onFileChange(File arg0) {
		onFileChangeAction(arg0);
	}

	public abstract void onFileChangeAction(File arg0);

	@Override
	public void onFileCreate(File arg0) {
		
	}

	@Override
	public void onFileDelete(File arg0) {
		
	}

	@Override
	public void onStart(FileAlterationObserver arg0) {
		
	}

	@Override
	public void onStop(FileAlterationObserver arg0) {
	}

}
