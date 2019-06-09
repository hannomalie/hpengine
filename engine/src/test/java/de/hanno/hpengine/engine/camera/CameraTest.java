package de.hanno.hpengine.engine.camera;

import de.hanno.hpengine.TestHelpers;
import de.hanno.hpengine.TestWithRenderer;
import de.hanno.hpengine.engine.entity.Entity;
import de.hanno.hpengine.engine.scene.AABB;
import de.hanno.hpengine.util.Util;
import junit.framework.Assert;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;

public class CameraTest extends TestWithRenderer {
	
	@Test
	public void rotation() {
		Camera camera = new Camera(new Entity(), engine.config);
        Assert.assertEquals(new Vector3f(0,1,0), camera.getUpDirection());
        Assert.assertEquals(new Vector3f(1,0,0), camera.getRightDirection());
        Assert.assertEquals(new Vector3f(0,0,-1), camera.getViewDirection());
		
		camera.rotate(new AxisAngle4f(0,1,0, (float) Math.toRadians(90f)));

		float epsilon = 0.01f;
		Assert.assertEquals(0, camera.getUpDirection().x, epsilon);
		Assert.assertEquals(1, camera.getUpDirection().y, epsilon);
		Assert.assertEquals(0, camera.getUpDirection().z, epsilon);

		Assert.assertEquals(0, camera.getRightDirection().x, epsilon);
		Assert.assertEquals(0, camera.getRightDirection().y, epsilon);
		Assert.assertEquals(-1, camera.getRightDirection().z, epsilon);
		
		Assert.assertEquals(-1, camera.getViewDirection().x, epsilon);
		Assert.assertEquals(0, camera.getViewDirection().y, epsilon);
		Assert.assertEquals(0, camera.getViewDirection().z, epsilon);
	}
	
	@Test
	public void inFrustum() {
		Matrix4f projectionMatrix = Util.createPerspective(60, 16/9, 0.1f, 100f);
		Camera camera = new Camera(new Entity(), projectionMatrix, 0.1f, 100f, 60, 16/9);
		Assert.assertEquals(new Vector3f(0,0,-1), camera.getViewDirection());
		
		Frustum frustum = camera.getFrustum();

		Assert.assertEquals(-0.1f, frustum.values[Frustum.FRONT][Frustum.D], 0.0001f); // z is negative, again...
		Assert.assertEquals(100f, frustum.values[Frustum.BACK][Frustum.D], 0.0001f); // distance is always positive value
		Assert.assertFalse(frustum.pointInFrustum(0, 0, 1));
		Assert.assertTrue(frustum.pointInFrustum(0, 0, -1));

		camera.translateLocal(new Vector3f(0,0,5));
		frustum.calculate(camera);
		TestHelpers.assertEpsilonEqual(new Vector3f(0, 0, -1), camera.getViewDirection(), 0.01f);
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));

		camera.setTranslation(new Vector3f());
		camera.rotate(new AxisAngle4f(0, 1, 0, (float) Math.toRadians(180))); // cam is now at pos 0,0,0, rotated 180 degrees, looking in +z
		TestHelpers.assertEpsilonEqual(new Vector3f(0, 0, 1), camera.getViewDirection(), 0.01f);
		Assert.assertTrue(frustum.pointInFrustum(0, 0, 1));
		Assert.assertTrue(frustum.sphereInFrustum(0, 0, 1, 1));
		Assert.assertTrue(frustum.cubeInFrustum(new Vector3f(0, 0, 1), 1));
		Assert.assertTrue(frustum.boxInFrustum(new AABB(new Vector3f(0, 0, 1), 1)));

		frustum.calculate(camera);
		Assert.assertFalse(frustum.pointInFrustum(0, 0, -1));
		Assert.assertFalse(frustum.sphereInFrustum(0, 0, -2, 1));
		Assert.assertFalse(frustum.cubeInFrustum(new Vector3f(0, 0, -2), 1));
		Assert.assertFalse(frustum.boxInFrustum(new AABB(new Vector3f(0, 0, -2), 1)));
		Assert.assertFalse(new AABB(new Vector3f(0, 0, -2), 1).isInFrustum(camera));
		Assert.assertTrue(new AABB(new Vector3f(0, 0, 0), 10).isInFrustum(camera));
	}
}
