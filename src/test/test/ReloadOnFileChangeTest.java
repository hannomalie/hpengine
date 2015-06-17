package test;

import engine.World;
import jdk.nashorn.internal.ir.annotations.Ignore;
import junit.framework.Assert;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import renderer.DeferredRenderer;
import renderer.Renderer;
import util.ressources.FileMonitor;
import util.ressources.ReloadOnFileChangeListener;
import util.ressources.Reloadable;

import java.io.File;
import java.io.IOException;

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
		}
		
		DummyReloadable loadable = new DummyReloadable();
		
		ReloadOnFileChangeListener.createAndAddToMonitor(simpleFile.getParent(), loadable);

		FileUtils.writeStringToFile(simpleFile, "yyy");
		FileUtils.touch(simpleFile);
		FileMonitor.getInstance().checkAndNotify();
		
		Assert.assertTrue(loadable.reloaded);
	}

}
