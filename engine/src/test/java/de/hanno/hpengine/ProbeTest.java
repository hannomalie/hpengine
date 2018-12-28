package de.hanno.hpengine;

import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.scene.EnvironmentProbe;
import de.hanno.hpengine.engine.scene.EnvironmentProbe.Update;
import de.hanno.hpengine.engine.transform.AABB;
import junit.framework.Assert;
import org.joml.Vector3f;
import org.junit.Test;

public class ProbeTest extends TestWithEngine {
	
	@Test
	public void assignsProbesProperly() throws Exception {
        EnvironmentProbe probeNear = engine.getScene().getEnvironmentProbeManager().getProbe(new Entity(), new Vector3f(), 20, Update.STATIC, 1, engine.getScene().getEnvironmentProbeManager().engine.getRenderer());
		Entity farEntity = new Entity();
		farEntity.translate(new Vector3f(10,0,0));
		EnvironmentProbe probeFar = engine.getScene().getEnvironmentProbeManager().getProbe(farEntity, new Vector3f(10,0,0), 100, Update.STATIC, 1, engine.getScene().getEnvironmentProbeManager().engine.getRenderer());
		
		Entity centeredEntity = new Entity() {
			@Override public void setSelected(boolean selected) { }
			@Override public boolean isSelected() { return false; }
			@Override public String getName() { return null; }
			
			@Override
			public AABB getMinMaxWorld() {
				return new AABB(
						new Vector3f(-1, -1, -1),
						new Vector3f(1, 1, 1));
			}
		};

		Assert.assertTrue(probeNear.contains(centeredEntity.getMinMaxWorld()));
		Assert.assertTrue(probeFar.contains(centeredEntity.getMinMaxWorld()));
        Assert.assertEquals(probeNear, engine.getScene().getEnvironmentProbeManager().getProbeForEntity(centeredEntity).get());

        centeredEntity.translateLocal(new Vector3f(10,0,0));
        Assert.assertEquals(probeFar, engine.getScene().getEnvironmentProbeManager().getProbeForEntity(centeredEntity).get());
	}
}
