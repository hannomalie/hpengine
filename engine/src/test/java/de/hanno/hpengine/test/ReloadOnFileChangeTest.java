package de.hanno.hpengine.test;

import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import de.hanno.hpengine.util.ressources.FileMonitor;
import de.hanno.hpengine.util.ressources.ReloadOnFileChangeListener;
import de.hanno.hpengine.util.ressources.Reloadable;

import java.io.File;

public class ReloadOnFileChangeTest extends TestWithRenderer {
	
	private static File simpleFile;


	@Test
	public void reloadProgramTest() throws Exception {
		simpleFile = new File("testfolder/test.file");
		FileUtils.writeStringToFile(simpleFile, "xxx");

		FileMonitor.getInstance().running = true;
		
		class DummyReloadable implements Reloadable {
			public boolean reloaded = false;
			
			@Override
			public void load() {
				reloaded = true;
			}

			@Override
			public void unload() {
				
			}

			@Override
			public String getName() {
				return "Dummy";
			}
		}
		
		DummyReloadable loadable = new DummyReloadable();
		
		ReloadOnFileChangeListener.createAndAddToMonitor(simpleFile.getParent(), loadable);

		FileUtils.writeStringToFile(simpleFile, "yyy");
		FileUtils.touch(simpleFile);
		FileMonitor.getInstance().checkAndNotify();
		
		Assert.assertTrue(loadable.reloaded);
	}

}
