package test;

import junit.framework.Assert;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.AreaLight;
import main.renderer.light.Spotlight;
import main.renderer.light.TubeLight;

import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class LightTest {

	private static Renderer renderer;

	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new Spotlight(true));
	}

	@Test
	public void tubeLightHasCorrectProportions() {
		TubeLight tubeLight = renderer.getLightFactory().getTubeLight();
		
		Assert.assertEquals(new Vector3f(), tubeLight.getPosition());
		Assert.assertEquals(200, tubeLight.getLength(), 1.0f);
		Assert.assertEquals(50, tubeLight.getRadius(), 1.0f);
		
		Assert.assertEquals(new Vector3f(-50,0,0), tubeLight.getStart());
		Assert.assertEquals(new Vector3f(50,0,0), tubeLight.getEnd());
		Assert.assertEquals(new Vector3f(200,100,100), tubeLight.getScale());
		
		Vector4f[] minMaxWorld = tubeLight.getMinMaxWorld();
		Assert.assertEquals(new Vector4f(-100,-50,-50,0), minMaxWorld[0]);
		Assert.assertEquals(new Vector4f(100,50,50,0), minMaxWorld[1]);
	}
	
	@Test
	public void areaLightHasCorrectProportions() {
		AreaLight areaLight = renderer.getLightFactory().getAreaLight(100,100,200);
		
		Assert.assertEquals(new Vector3f(), areaLight.getPosition());
		Assert.assertEquals(100, areaLight.getWidth(), 1.0f);
		Assert.assertEquals(100, areaLight.getHeight(), 1.0f);
		Assert.assertEquals(200, areaLight.getRange(), 1.0f);

		Assert.assertEquals(new Vector3f(500,500,400), areaLight.getScale());
		
		Vector4f[] minMaxWorld = areaLight.getMinMaxWorld();
		Assert.assertEquals(new Vector4f(-250,-250,-200,0), minMaxWorld[0]);
		Assert.assertEquals(new Vector4f(250,250,200,0), minMaxWorld[1]);
	}

}
