package de.hanno.hpengine.test;

import de.hanno.hpengine.engine.Transform;
import de.hanno.hpengine.engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import de.hanno.hpengine.scene.EnvironmentProbe;
import de.hanno.hpengine.scene.EnvironmentProbe.Update;
import de.hanno.hpengine.scene.EnvironmentProbeFactory;

public class ProbeTest extends TestWithRenderer {
	
	@Test
	public void assignsProbesProperly() throws Exception {
        EnvironmentProbe probeNear = EnvironmentProbeFactory.getInstance().getProbe(new Vector3f(), 20, Update.STATIC, 1);
        EnvironmentProbe probeFar = EnvironmentProbeFactory.getInstance().getProbe(new Vector3f(10,0,0), 100, Update.STATIC, 1);
		
		Entity centeredEntity = new Entity() {
			Transform transform = new Transform();
			
			@Override public void setTransform(Transform transform) { }
			@Override public void setSelected(boolean selected) { }
			@Override public boolean isSelected() { return false; }
			@Override public Transform getTransform() { return transform; }
			@Override public String getName() { return null; }
			
			@Override
			public Vector4f[] getMinMaxWorld() {
				return new Vector4f[] {
						new Vector4f(-1, -1, -1, 0),
						new Vector4f(1, 1, 1, 0)};
			}
		};

		Assert.assertTrue(probeNear.contains(centeredEntity.getMinMaxWorld()));
		Assert.assertTrue(probeFar.contains(centeredEntity.getMinMaxWorld()));
        Assert.assertEquals(probeNear, EnvironmentProbeFactory.getInstance().getProbeForEntity(centeredEntity).get());
		
		centeredEntity.move(new Vector3f(10,0,0));
        Assert.assertEquals(probeFar, EnvironmentProbeFactory.getInstance().getProbeForEntity(centeredEntity).get());
	}
}