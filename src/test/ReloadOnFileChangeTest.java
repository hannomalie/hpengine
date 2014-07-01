package test;

import java.io.File;
import java.io.IOException;

import jdk.nashorn.internal.ir.annotations.Ignore;
import junit.framework.Assert;
import main.World;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.Spotlight;
import main.util.ressources.FileMonitor;
import main.util.ressources.ReloadOnFileChangeListener;
import main.util.ressources.Reloadable;

import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReloadOnFileChangeTest {
	
	private static Renderer renderer;
	private static File simpleFile;

	@BeforeClass
	public static void init() throws IOException {
		renderer = new DeferredRenderer(new Spotlight(true));
		
		simpleFile = new File("testfolder/test.file");
		FileUtils.writeStringToFile(simpleFile, "xxx");
		
	}
	
	@Test
	@Ignore
	public void reloadProgramTest() throws Exception {
		World.RELOAD_ON_FILE_CHANGE = true;
		
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
