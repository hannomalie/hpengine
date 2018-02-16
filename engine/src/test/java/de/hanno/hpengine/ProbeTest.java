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
        EnvironmentProbe probeNear = engine.getEnvironmentProbeManager().getProbe(new Vector3f(), 20, Update.STATIC, 1);
        EnvironmentProbe probeFar = engine.getEnvironmentProbeManager().getProbe(new Vector3f(10,0,0), 100, Update.STATIC, 1);
		
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
        Assert.assertEquals(probeNear, engine.getEnvironmentProbeManager().getProbeForEntity(centeredEntity).get());

        centeredEntity.translateLocal(new Vector3f(10,0,0));
        Assert.assertEquals(probeFar, engine.getEnvironmentProbeManager().getProbeForEntity(centeredEntity).get());
	}
}
