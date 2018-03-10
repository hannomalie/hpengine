package de.hanno.hpengine;

import de.hanno.hpengine.engine.graphics.light.AreaLight;
import de.hanno.hpengine.engine.graphics.light.TubeLight;
import de.hanno.hpengine.engine.transform.AABB;
import junit.framework.Assert;
import org.joml.Vector3f;
import org.junit.Test;

public class LightTest extends TestWithEngine {

	@Test
	public void tubeLightHasCorrectProportions() {
		TubeLight tubeLight = engine.getScene().getLightManager().getTubeLight(100, 50);
		
		Assert.assertEquals(new Vector3f(), tubeLight.getPosition());
		Assert.assertEquals(200, tubeLight.getLength(), 1.0f);
		Assert.assertEquals(50, tubeLight.getRadius(), 1.0f);
		
		Assert.assertEquals(new Vector3f(-50,0,0), tubeLight.getStart());
		Assert.assertEquals(new Vector3f(50,0,0), tubeLight.getEnd());
		Assert.assertEquals(new Vector3f(200,100,100), tubeLight.getScale());
		
		AABB minMaxWorld = tubeLight.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-100,-50,-50), minMaxWorld.getMin());
		Assert.assertEquals(new Vector3f(100,50,50), minMaxWorld.getMax());
	}
	
	@Test
	public void areaLightHasCorrectProportions() {
        AreaLight areaLight = engine.getScene().getLightManager().getAreaLight(100,100,200);
		
		Assert.assertEquals(new Vector3f(), areaLight.getPosition());
		Assert.assertEquals(100, areaLight.getWidth(), 1.0f);
		Assert.assertEquals(100, areaLight.getHeight(), 1.0f);
		Assert.assertEquals(200, areaLight.getRange(), 1.0f);

		Assert.assertEquals(new Vector3f(100,100,200), areaLight.getScale());
		
//		Vector4f[] minMaxWorldProperty = areaLight.getMinMaxWorldProperty();
//		Assert.assertEquals(new Vector4f(-250,-250,-200,0), minMaxWorldProperty[0]);
//		Assert.assertEquals(new Vector4f(250,250,200,0), minMaxWorldProperty[1]);
	}

}
