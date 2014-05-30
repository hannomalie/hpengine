package test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javafx.scene.paint.StopBuilder;
import main.DeferredRenderer;
import main.Spotlight;
import main.util.Texture;
import main.util.TextureLoader;
import main.util.Util;
import main.util.stopwatch.StopWatch;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.sun.scenario.effect.light.SpotLight;

public class TextureTest {
	
	private static DeferredRenderer renderer;

	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new Spotlight(true));
	}

	@Test
	public void writeAndRead() throws IOException, ClassNotFoundException {
		Texture texture = Util.loadTexture("wood_diffuse.png");
		
		byte[] data = texture.getData();
		
		String filename = "wood_diffuse.hptexture";

	    // save the object to file
	    FileOutputStream fos = null;
	    ObjectOutputStream out = null;
	      fos = new FileOutputStream(filename);
	      out = new ObjectOutputStream(fos);
	      out.writeObject(texture);

	      out.close();
	      
	    FileInputStream fis = null;
	    ObjectInputStream in = null;
	      fis = new FileInputStream(filename);
	      in = new ObjectInputStream(fis);
	      texture = (Texture) in.readObject();
	      in.close();
	      texture.upload();
	    
		StopWatch.ACTIVE = false;
	    Assert.assertArrayEquals(data, texture.getData());
	    
	}
}
