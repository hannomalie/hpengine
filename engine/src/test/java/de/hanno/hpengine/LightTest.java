package de.hanno.hpengine;

import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.graphics.light.area.AreaLight;
import de.hanno.hpengine.engine.graphics.light.tube.TubeLight;
import de.hanno.hpengine.engine.transform.AABB;
import junit.framework.Assert;
import org.joml.Vector3f;
import org.junit.Test;

public class LightTest extends TestWithEngine {

	@Test
	public void tubeLightHasCorrectProportions() {
		Entity tubeLightEntity = new Entity();
		TubeLight tubeLight = engine.getScene().getDirectionalLightSystem().getTubeLight(tubeLightEntity, 100, 50);
		
		Assert.assertEquals(new Vector3f(), tubeLight.getEntity().getPosition());
		Assert.assertEquals(200, tubeLight.getLength(), 1.0f);
		Assert.assertEquals(50, tubeLight.getRadius(), 1.0f);
		
		Assert.assertEquals(new Vector3f(-50,0,0), tubeLight.getStart());
		Assert.assertEquals(new Vector3f(50,0,0), tubeLight.getEnd());
		Assert.assertEquals(200, tubeLight.getLength());
		Assert.assertEquals(50, tubeLight.getRadius());
		
		AABB minMaxWorld = tubeLight.getMinMaxWorld();
		Assert.assertEquals(new Vector3f(-100,-50,-50), minMaxWorld.getMin());
		Assert.assertEquals(new Vector3f(100,50,50), minMaxWorld.getMax());
	}
	
	@Test
	public void areaLightHasCorrectProportions() {
        AreaLight areaLight = engine.getScene().getDirectionalLightSystem().getAreaLight(new Entity(), new Vector3f(1,1,1), 100,100,200);
		
		Assert.assertEquals(new Vector3f(), areaLight.getEntity().getPosition());
		Assert.assertEquals(100, areaLight.getWidth(), 1.0f);
		Assert.assertEquals(100, areaLight.getHeight(), 1.0f);
		Assert.assertEquals(200, areaLight.getRange(), 1.0f);

		Assert.assertEquals(new Vector3f(100,100,200), areaLight.getScale());
		
//		Vector4f[] minMaxWorldProperty = areaLight.getMinMaxWorldProperty();
//		Assert.assertEquals(new Vector4f(-250,-250,-200,0), minMaxWorldProperty[0]);
//		Assert.assertEquals(new Vector4f(250,250,200,0), minMaxWorldProperty[1]);
	}

}
