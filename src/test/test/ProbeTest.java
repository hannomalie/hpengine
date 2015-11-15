package test;

import engine.Transform;
import engine.model.Entity;
import junit.framework.Assert;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;
import scene.EnvironmentProbe;
import scene.EnvironmentProbe.Update;

public class ProbeTest extends TestWithRenderer {
	
	@Test
	public void assignsProbesProperly() throws Exception {
		EnvironmentProbe probeNear = renderer.getEnvironmentProbeFactory().getProbe(new Vector3f(), 20, Update.STATIC, 1);
		EnvironmentProbe probeFar = renderer.getEnvironmentProbeFactory().getProbe(new Vector3f(10,0,0), 100, Update.STATIC, 1);
		
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
		Assert.assertEquals(probeNear, renderer.getEnvironmentProbeFactory().getProbeForEntity(centeredEntity).get());
		
		centeredEntity.move(new Vector3f(10,0,0));
		Assert.assertEquals(probeFar, renderer.getEnvironmentProbeFactory().getProbeForEntity(centeredEntity).get());
	}
}
