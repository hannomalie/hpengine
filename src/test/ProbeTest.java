package test;

import junit.framework.Assert;
import main.Transform;
import main.model.IEntity;
import main.renderer.DeferredRenderer;
import main.renderer.Renderer;
import main.renderer.light.DirectionalLight;
import main.renderer.material.Material;
import main.scene.EnvironmentProbe;
import main.scene.EnvironmentProbe.Update;

import org.junit.BeforeClass;
import org.junit.Test;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

public class ProbeTest {
	static Renderer renderer;
	
	@BeforeClass
	public static void init() {
		renderer = new DeferredRenderer(new DirectionalLight(true));
	}
	
	@Test
	public void assignsProbesProperly() {
		EnvironmentProbe probeNear = renderer.getEnvironmentProbeFactory().getProbe(new Vector3f(), 20, Update.STATIC);
		EnvironmentProbe probeFar = renderer.getEnvironmentProbeFactory().getProbe(new Vector3f(10,0,0), 100, Update.STATIC);
		
		IEntity centeredEntity = new IEntity() {
			Transform transform = new Transform();
			
			@Override public void setTransform(Transform transform) { }
			@Override public void setSelected(boolean selected) { }
			@Override public boolean isSelected() { return false; }
			@Override public Transform getTransform() { return transform; }
			@Override public String getName() { return null; }
			
			@Override
			public Material getMaterial() {
				return renderer.getMaterialFactory().getDefaultMaterial();
			}

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
